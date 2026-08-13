package com.jd.genie.platform.phase2.runtime.orchestration;

import com.jd.genie.model.response.GptProcessResult;
import com.jd.genie.platform.agentbridge.AgentBridgeException;
import com.jd.genie.platform.agentbridge.ConversationStreamObserver;
import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2.runtime.agent.AgentTaskResult;
import com.jd.genie.platform.phase2.runtime.event.OrchestrationEventMapper;
import com.jd.genie.platform.phase2.runtime.plan.OrchestrationPlan;
import com.jd.genie.platform.phase2.runtime.plan.OrchestrationPlanValidator;
import com.jd.genie.platform.phase2.runtime.plan.OrchestrationStep;
import com.jd.genie.platform.phase2.runtime.plan.OrchestrationSubTask;
import com.jd.genie.platform.phase2.runtime.route.RouteDecision;
import com.jd.genie.platform.phase2.runtime.trace.OrchestrationTraceChannel;
import com.jd.genie.platform.phase2contract.dto.AgentCapabilitySummary;
import com.jd.genie.platform.phase2contract.dto.OrchestrationPlanStepView;
import com.jd.genie.platform.phase2contract.dto.OrchestrationSubTaskView;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public final class Phase2OrchestrationRuntime {

    private final OrchestrationModelPort modelPort;
    private final OrchestrationPlanValidator planValidator;
    private final SerialOrchestrationService serialService;
    private final OrchestrationEventMapper eventMapper;
    private final DirectFallbackExecutor directFallbackExecutor;

    public Phase2OrchestrationRuntime(
            OrchestrationModelPort modelPort,
            OrchestrationPlanValidator planValidator,
            SerialOrchestrationService serialService,
            OrchestrationEventMapper eventMapper
    ) {
        this(modelPort, planValidator, serialService, eventMapper, null);
    }

    public Phase2OrchestrationRuntime(
            OrchestrationModelPort modelPort,
            OrchestrationPlanValidator planValidator,
            SerialOrchestrationService serialService,
            OrchestrationEventMapper eventMapper,
            DirectFallbackExecutor directFallbackExecutor
    ) {
        this.modelPort = modelPort;
        this.planValidator = planValidator;
        this.serialService = serialService;
        this.eventMapper = eventMapper;
        this.directFallbackExecutor = directFallbackExecutor;
    }

    public RouteDecision selectRoute(
            String mode,
            String query,
            String conversationSummary,
            List<AgentCapabilitySummary> candidates
    ) {
        return selectRouteDecision(mode, query, conversationSummary, candidates);
    }

    /**
     * Compatibility boundary for callers that do not yet provide local memory context.
     * The orchestration data flow remains identical; absent context is represented explicitly.
     */
    public void execute(
            CurrentUser user,
            String requestId,
            String runId,
            String query,
            String conversationSummary,
            List<AgentCapabilitySummary> candidates,
            RouteDecision route,
            ConversationStreamObserver observer
    ) {
        execute(user, requestId, runId, query, conversationSummary, null, candidates, route, observer);
    }

    public void execute(
            CurrentUser user,
            String requestId,
            String runId,
            String query,
            String conversationSummary,
            String longTermMemory,
            List<AgentCapabilitySummary> candidates,
            RouteDecision route,
            ConversationStreamObserver observer
    ) {
        Map<String, String> failures = new LinkedHashMap<>();
        AtomicLong sequence = new AtomicLong();
        OrchestrationTraceChannel traces = new OrchestrationTraceChannel(observer, requestId, runId, sequence);
        try {
            emit(observer, requestId, runId, sequence, "ROUTE_SELECTED", Map.of(
                    "route", route.route().name(), "reasonCode", route.reasonCode()
            ), List.of());
            traces.emitMain(OrchestrationTraceChannel.KIND_STATUS,
                    "路由决策：" + route.route().name() + "（" + route.reasonCode() + "）", false);
            if (route.route() != RouteDecision.Route.ORCHESTRATED) {
                throw new AgentBridgeException(MvpErrorCode.INTERNAL_ERROR, "DIRECT route must use the existing V1 execution path");
            }

            Map<String, String> successes = new LinkedHashMap<>();
            OrchestrationPlan plan = planValidator.validate(
                    modelPort.createPlan(query, candidates, 1, Map.of(), Map.of()),
                    candidates
            );
            List<OrchestrationPlanStepView> steps = plan.steps().stream()
                    .map(step -> stepView(step, candidates))
                    .toList();
            emit(observer, requestId, runId, sequence, "PLAN_CREATED", Map.of("attemptNo", 1), steps);
            traces.emitMain(1, OrchestrationTraceChannel.KIND_OUTPUT, formatPlanTrace(steps), false);
            java.util.concurrent.atomic.AtomicBoolean degraded = new java.util.concurrent.atomic.AtomicBoolean(false);
            Map<String, AgentTaskResult> results = serialService.execute(
                    user, query, longTermMemory, plan.steps(),
                    (eventType, step, result, details) -> {
                        if ("STEP_DEGRADED".equals(eventType)) {
                            degraded.set(true);
                        }
                        emitStep(
                                observer, requestId, runId, sequence, eventType, 1, step, result, details, steps
                        );
                    },
                    observer::isTerminal,
                    new LinkedHashMap<>(),
                    traces,
                    1,
                    observer
            );
            collectResults(results, successes, failures);
            boolean hadDegraded = degraded.get();
            complete(observer, requestId, runId, sequence, query, successes, failures,
                    failures.isEmpty() && !hadDegraded ? "SUCCESS" : "PARTIAL", 1, traces);
        } catch (RuntimeException error) {
            if (!observer.isTerminal()) {
                observer.onError(controlledFailure(error));
            }
        }
    }

    private AgentBridgeException controlledFailure(RuntimeException error) {
        MvpErrorCode errorCode = error instanceof AgentBridgeException bridgeException
                ? bridgeException.getErrorCode()
                : MvpErrorCode.INTERNAL_ERROR;
        return new AgentBridgeException(errorCode, errorCode.name(), error);
    }

    private RouteDecision selectRouteDecision(
            String mode,
            String query,
            String conversationSummary,
            List<AgentCapabilitySummary> candidates
    ) {
        if ("ORCHESTRATED".equals(mode)) {
            return new RouteDecision(RouteDecision.Route.ORCHESTRATED, "FORCED_BY_REQUEST");
        }
        try {
            return modelPort.selectRoute(query, conversationSummary, candidates);
        } catch (RuntimeException ignored) {
            return new RouteDecision(RouteDecision.Route.DIRECT, "ROUTER_FALLBACK");
        }
    }

    private void emitStep(
            ConversationStreamObserver observer,
            String requestId,
            String runId,
            AtomicLong sequence,
            String eventType,
            int attemptNo,
            OrchestrationStep step,
            AgentTaskResult result,
            Map<String, Object> details,
            List<OrchestrationPlanStepView> steps
    ) {
        Map<String, Object> eventDetails = details == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(details);
        eventDetails.put("attemptNo", attemptNo);
        eventDetails.put("stepId", step.stepId());
        eventDetails.put("stepMode", step.mode() == null ? null : step.mode().name());
        // SUBTASK_* events already carry the subTask agent in details; step-level
        // events fall back to the top-level step agent.
        eventDetails.putIfAbsent("agentId", step.agentId());
        Object named = eventDetails.get("agentName");
        eventDetails.putIfAbsent("agentName", named instanceof String name && !name.isBlank() ? name : step.agentId());
        if (result != null && result.status() == AgentTaskResult.Status.FAILURE) {
            eventDetails.put("errorCode", result.errorCode());
        }
        emit(observer, requestId, runId, sequence, eventType, eventDetails, steps);
    }

    private void collectResults(
            Map<String, AgentTaskResult> results,
            Map<String, String> successes,
            Map<String, String> failures
    ) {
        results.forEach((stepId, result) -> {
            if (result.status() == AgentTaskResult.Status.SUCCESS) {
                successes.put(stepId, result.output());
            } else {
                failures.put(stepId, result.errorCode());
            }
        });
    }

    private void complete(
            ConversationStreamObserver observer,
            String requestId,
            String runId,
            AtomicLong sequence,
            String query,
            Map<String, String> successes,
            Map<String, String> failures,
            String completionStatus,
            int attemptNo,
            OrchestrationTraceChannel traces
    ) {
        emit(observer, requestId, runId, sequence, "SUMMARY_STARTED", Map.of("attemptNo", attemptNo), List.of());
        traces.emitMain(attemptNo, OrchestrationTraceChannel.KIND_STATUS, "正在汇总各 Agent 结果…", false);
        // Quote real step outputs for 主要结果 — LLM must not rewrite/fabricate them.
        String factual = labeledDeterministicSummary(successes, failures);
        String answer;
        try {
            String overview = modelPort.summarize(query, Map.copyOf(successes), Map.copyOf(failures));
            answer = mergeFactualWithOverview(factual, overview);
            emit(observer, requestId, runId, sequence, "SUMMARY_COMPLETED", Map.of("attemptNo", attemptNo), List.of());
            traces.emitMain(attemptNo, OrchestrationTraceChannel.KIND_STATUS, "汇总完成", false);
        } catch (RuntimeException ignored) {
            answer = factual;
            emit(observer, requestId, runId, sequence, "SUMMARY_FALLBACK", Map.of(
                    "attemptNo", attemptNo, "reasonCode", "SUMMARY_FAILED"
            ), List.of());
            traces.emitMain(attemptNo, OrchestrationTraceChannel.KIND_STATUS, "汇总失败，已使用兜底摘要", false);
        }
        GptProcessResult finalResponse = eventMapper.finalResponse(
                requestId,
                runId,
                sequence.incrementAndGet(),
                answer,
                completionStatus
        );
        observer.onEvent(finalResponse);
        observer.onCompleted();
    }

    private String formatPlanTrace(List<OrchestrationPlanStepView> steps) {
        if (steps == null || steps.isEmpty()) {
            return "计划为空";
        }
        return steps.stream()
                .map(step -> {
                    String name = step.agentName() == null || step.agentName().isBlank()
                            ? step.agentId()
                            : step.agentName();
                    return "- [" + step.stepId() + "] " + name + "：" + step.objective();
                })
                .collect(Collectors.joining("\n", "任务安排：\n", ""));
    }

    private void emit(
            ConversationStreamObserver observer,
            String requestId,
            String runId,
            AtomicLong sequence,
            String eventType,
            Map<String, Object> details,
            List<OrchestrationPlanStepView> steps
    ) {
        observer.onEvent(eventMapper.progress(requestId, runId, sequence.incrementAndGet(), eventType, details, steps));
    }

    private OrchestrationPlanStepView stepView(
            OrchestrationStep step,
            List<AgentCapabilitySummary> candidates
    ) {
        String agentName = agentName(step.agentId(), candidates);
        List<OrchestrationSubTaskView> subTasks = step.subTasks().stream()
                .map(subTask -> subTaskView(subTask, candidates))
                .toList();
        return new OrchestrationPlanStepView(
                step.stepId(),
                step.agentId(),
                agentName,
                step.objective(),
                step.inputRefs(),
                step.mode(),
                subTasks
        );
    }

    private OrchestrationSubTaskView subTaskView(
            OrchestrationSubTask subTask,
            List<AgentCapabilitySummary> candidates
    ) {
        return new OrchestrationSubTaskView(
                subTask.subTaskId(),
                subTask.agentId(),
                agentName(subTask.agentId(), candidates),
                subTask.objective()
        );
    }

    private String agentName(String agentId, List<AgentCapabilitySummary> candidates) {
        if (agentId == null || agentId.isBlank() || candidates == null) {
            return agentId;
        }
        for (AgentCapabilitySummary candidate : candidates) {
            if (candidate != null && agentId.equals(candidate.agentId())) {
                if (candidate.name() != null && !candidate.name().isBlank()) {
                    return candidate.name();
                }
                break;
            }
        }
        return agentId;
    }

    private String deterministicSummary(Map<String, String> successes, Map<String, String> failures) {
        return labeledDeterministicSummary(successes, failures);
    }

    private String labeledDeterministicSummary(
            Map<String, String> successes,
            Map<String, String> failures
    ) {
        String completed = successes.isEmpty() ? "无" : String.join("\n", successes.keySet());
        String results;
        if (successes.isEmpty()) {
            results = "无";
        } else {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> entry : successes.entrySet()) {
                if (!sb.isEmpty()) {
                    sb.append('\n');
                }
                sb.append("- ").append(entry.getKey()).append(": ")
                        .append(entry.getValue() == null ? "" : entry.getValue());
            }
            results = sb.toString();
        }
        String unfinished = failures.isEmpty() ? "无" : String.join("\n", failures.keySet());
        String required = failures.isEmpty()
                ? "无，所有任务均已顺利完成。"
                : String.join("\n", failures.values());
        return "## 已完成\n" + completed
                + "\n\n## 主要结果\n" + results
                + "\n\n## 未完成\n" + unfinished
                + "\n\n## 继续完成所需\n" + required;
    }

    private String mergeFactualWithOverview(String factual, String overview) {
        if (overview == null || overview.isBlank()) {
            return factual;
        }
        String trimmed = overview.trim();
        String overviewBody = trimmed;
        int idx = trimmed.indexOf("## 汇总");
        if (idx >= 0) {
            overviewBody = trimmed.substring(idx + "## 汇总".length()).trim();
            int next = overviewBody.indexOf("\n## ");
            if (next >= 0) {
                overviewBody = overviewBody.substring(0, next).trim();
            }
        } else if (trimmed.startsWith("## ")) {
            // Full markdown from model — keep only a short prose paragraph if present.
            overviewBody = trimmed.replaceAll("(?m)^## .*\\R?", "").trim();
        }
        if (overviewBody.isBlank()) {
            return factual;
        }
        return factual.replace(
                "\n\n## 未完成\n",
                "\n\n## 汇总\n" + overviewBody + "\n\n## 未完成\n"
        );
    }
}
