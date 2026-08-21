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
import com.jd.genie.platform.conversation.attachment.ChatAttachmentPrompt;
import com.jd.genie.platform.phase2.runtime.resource.SystemResourceBuilder;
import com.jd.genie.platform.phase2.runtime.route.DispatchDecision;
import com.jd.genie.platform.phase2.runtime.route.RouteDecision;
import com.jd.genie.platform.phase2.runtime.trace.OrchestrationTraceChannel;
import com.jd.genie.platform.phase2contract.dto.AgentCapabilitySummary;
import com.jd.genie.platform.phase2contract.dto.MasterPersona;
import com.jd.genie.platform.phase2contract.dto.OrchestrationPlanStepView;
import com.jd.genie.platform.phase2contract.dto.OrchestrationSubTaskView;
import com.jd.genie.platform.phase2contract.dto.TeamCapabilitySummary;
import com.jd.genie.platform.phase2contract.enums.StepMode;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Slf4j
public final class Phase2OrchestrationRuntime {

    private final OrchestrationModelPort modelPort;
    private final OrchestrationPlanValidator planValidator;
    private final SerialOrchestrationService serialService;
    private final OrchestrationEventMapper eventMapper;

    public Phase2OrchestrationRuntime(
            OrchestrationModelPort modelPort,
            OrchestrationPlanValidator planValidator,
            SerialOrchestrationService serialService,
            OrchestrationEventMapper eventMapper
    ) {
        this.modelPort = modelPort;
        this.planValidator = planValidator;
        this.serialService = serialService;
        this.eventMapper = eventMapper;
    }

    public RouteDecision selectRoute(
            String mode,
            String query,
            String conversationSummary,
            List<AgentCapabilitySummary> candidates
    ) {
        return selectRoute(mode, query, conversationSummary, "", candidates);
    }

    public RouteDecision selectRoute(
            String mode,
            String query,
            String conversationSummary,
            String conversationHistory,
            List<AgentCapabilitySummary> candidates
    ) {
        return selectRoute(mode, query, conversationSummary, conversationHistory, candidates, MasterPersona.none());
    }

    public RouteDecision selectRoute(
            String mode,
            String query,
            String conversationSummary,
            String conversationHistory,
            List<AgentCapabilitySummary> candidates,
            MasterPersona masterPersona
    ) {
        return selectRouteDecision(mode, query, conversationSummary, conversationHistory, candidates, masterPersona);
    }

    public DispatchDecision selectDispatch(
            String query,
            String conversationSummary,
            String conversationHistory,
            List<AgentCapabilitySummary> agents,
            List<TeamCapabilitySummary> teams
    ) {
        List<AgentCapabilitySummary> safeAgents = agents == null ? List.of() : agents;
        List<TeamCapabilitySummary> safeTeams = teams == null ? List.of() : teams;
        try {
            DispatchDecision decision = modelPort.selectDispatch(
                    ChatAttachmentPrompt.withoutUploadedFileBodies(query),
                    conversationSummary,
                    conversationHistory,
                    safeAgents,
                    safeTeams
            );
            if (decision != null && decision.targetId() != null && !decision.targetId().isBlank()) {
                return decision;
            }
        } catch (RuntimeException error) {
            log.warn("Dispatch call failed, using local fallback: {}", error.getMessage(), error);
        }
        return fallbackDispatch(safeAgents, safeTeams);
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
        execute(user, requestId, runId, query, conversationSummary, longTermMemory, "", candidates, route, observer);
    }

    public void execute(
            CurrentUser user,
            String requestId,
            String runId,
            String query,
            String conversationSummary,
            String longTermMemory,
            String conversationHistory,
            List<AgentCapabilitySummary> candidates,
            RouteDecision route,
            ConversationStreamObserver observer
    ) {
        execute(user, requestId, runId, query, conversationSummary, longTermMemory, conversationHistory,
                candidates, route, observer, MasterPersona.none(), null);
    }

    public void execute(
            CurrentUser user,
            String requestId,
            String runId,
            String query,
            String conversationSummary,
            String longTermMemory,
            String conversationHistory,
            List<AgentCapabilitySummary> candidates,
            RouteDecision route,
            ConversationStreamObserver observer,
            MasterPersona masterPersona,
            String teamId
    ) {
        execute(user, requestId, runId, query, conversationSummary, longTermMemory, conversationHistory,
                candidates, route, observer, masterPersona, teamId, query);
    }

    public void execute(
            CurrentUser user,
            String requestId,
            String runId,
            String query,
            String conversationSummary,
            String longTermMemory,
            String conversationHistory,
            List<AgentCapabilitySummary> candidates,
            RouteDecision route,
            ConversationStreamObserver observer,
            MasterPersona masterPersona,
            String teamId,
            String specialistQuery
    ) {
        execute(user, requestId, runId, query, conversationSummary, longTermMemory, conversationHistory,
                candidates, route, observer, masterPersona, teamId, specialistQuery, requestId);
    }

    public void execute(
            CurrentUser user,
            String requestId,
            String runId,
            String query,
            String conversationSummary,
            String longTermMemory,
            String conversationHistory,
            List<AgentCapabilitySummary> candidates,
            RouteDecision route,
            ConversationStreamObserver observer,
            MasterPersona masterPersona,
            String teamId,
            String specialistQuery,
            String fileSessionId
    ) {
        MasterPersona persona = masterPersona == null ? MasterPersona.none() : masterPersona;
        Map<String, String> failures = new LinkedHashMap<>();
        AtomicLong sequence = new AtomicLong();
        OrchestrationTraceChannel traces = new OrchestrationTraceChannel(
                observer, requestId, runId, sequence, persona.displayName());
        String planningQuery = ChatAttachmentPrompt.withoutUploadedFileBodies(query);
        String agentQuery = specialistQuery == null || specialistQuery.isBlank() ? query : specialistQuery;
        try {
            Map<String, Object> routeDetails = new LinkedHashMap<>();
            routeDetails.put("route", route.route().name());
            routeDetails.put("reasonCode", route.reasonCode());
            putTeamContext(routeDetails, persona, teamId);
            emit(observer, requestId, runId, sequence, "ROUTE_SELECTED", routeDetails, List.of());
            traces.emitMain(OrchestrationTraceChannel.KIND_STATUS, humanDispatchStatus(route, persona, teamId), false);
            if (route.route() != RouteDecision.Route.ORCHESTRATED) {
                throw new AgentBridgeException(MvpErrorCode.INTERNAL_ERROR, "DIRECT route must use the existing V1 execution path");
            }

            Map<String, String> successes = new LinkedHashMap<>();
            OrchestrationPlan modelPlan = modelPort.createPlan(
                    planningQuery, conversationHistory, longTermMemory, conversationSummary, candidates,
                    1, Map.of(), Map.of(), persona
            );
            OrchestrationPlan plan = planValidator.validate(
                    enforceSystemResourceStep(planningQuery, assignCandidatesToMainOnlySteps(modelPlan, candidates)), candidates);
            List<OrchestrationPlanStepView> steps = plan.steps().stream()
                    .map(step -> stepView(step, candidates))
                    .toList();
            Map<String, Object> planDetails = new LinkedHashMap<>();
            planDetails.put("attemptNo", 1);
            putTeamContext(planDetails, persona, teamId);
            emit(observer, requestId, runId, sequence, "PLAN_CREATED", planDetails, steps);
            traces.emitMain(1, OrchestrationTraceChannel.KIND_STATUS, humanPlanStatus(steps), false);
            java.util.concurrent.atomic.AtomicBoolean degraded = new java.util.concurrent.atomic.AtomicBoolean(false);
            // acceptDeliverables is invoked from PARALLEL_AGENTS worker threads.
            java.util.List<com.jd.genie.agent.dto.File> deliverableFiles =
                    java.util.Collections.synchronizedList(new java.util.ArrayList<>());
            Map<String, AgentTaskResult> results = serialService.execute(
                    user, agentQuery, conversationHistory, UntrustedLocalContext.body(longTermMemory, conversationSummary), plan.steps(),
                    new OrchestrationEventSink() {
                        @Override
                        public void emit(String eventType, OrchestrationStep step, AgentTaskResult result, Map<String, Object> details) {
                            if ("STEP_DEGRADED".equals(eventType)) {
                                degraded.set(true);
                            }
                            emitStep(
                                    observer, requestId, runId, sequence, eventType, 1, step, result, details, steps
                            );
                        }

                        @Override
                        public void acceptDeliverables(java.util.List<com.jd.genie.agent.dto.File> files) {
                            if (files != null) {
                                deliverableFiles.addAll(files);
                            }
                        }
                    },
                    observer::isTerminal,
                    new LinkedHashMap<>(),
                    traces,
                    1,
                    observer,
                    fileSessionId
            );
            collectResults(results, successes, failures);
            boolean hadDegraded = degraded.get();
            complete(
                    observer,
                    requestId,
                    runId,
                    sequence,
                    planningQuery,
                    conversationHistory,
                    longTermMemory,
                    conversationSummary,
                    steps,
                    results,
                    successes,
                    failures,
                    failures.isEmpty() && !hadDegraded ? "SUCCESS" : "PARTIAL",
                    1,
                    traces,
                    deliverableFiles,
                    persona
            );
        } catch (RuntimeException error) {
            log.warn("Orchestration failed: {}", error.getMessage(), error);
            if (!observer.isTerminal()) {
                observer.onError(controlledFailure(error));
            }
        }
    }

    public void executeSolo(
            CurrentUser user,
            String requestId,
            String runId,
            String query,
            String conversationSummary,
            String longTermMemory,
            String conversationHistory,
            AgentCapabilitySummary agent,
            RouteDecision route,
            ConversationStreamObserver observer,
            String specialistQuery
    ) {
        executeSolo(user, requestId, runId, query, conversationSummary, longTermMemory, conversationHistory,
                agent, route, observer, specialistQuery, requestId);
    }

    public void executeSolo(
            CurrentUser user,
            String requestId,
            String runId,
            String query,
            String conversationSummary,
            String longTermMemory,
            String conversationHistory,
            AgentCapabilitySummary agent,
            RouteDecision route,
            ConversationStreamObserver observer,
            String specialistQuery,
            String fileSessionId
    ) {
        if (agent == null || agent.agentId() == null || agent.agentId().isBlank()) {
            throw new AgentBridgeException(MvpErrorCode.NO_SUITABLE_AGENT, "No suitable online Agent is available");
        }
        MasterPersona persona = MasterPersona.none();
        AtomicLong sequence = new AtomicLong();
        OrchestrationTraceChannel traces = new OrchestrationTraceChannel(
                observer, requestId, runId, sequence, persona.displayName());
        String planningQuery = ChatAttachmentPrompt.withoutUploadedFileBodies(query);
        String agentQuery = specialistQuery == null || specialistQuery.isBlank() ? query : specialistQuery;
        RouteDecision effective = route == null
                ? new RouteDecision(RouteDecision.Route.ORCHESTRATED, "SOLO_AGENT")
                : route;
        try {
            Map<String, Object> routeDetails = new LinkedHashMap<>();
            routeDetails.put("route", RouteDecision.Route.ORCHESTRATED.name());
            routeDetails.put("reasonCode", effective.reasonCode());
            routeDetails.put("agentId", agent.agentId());
            routeDetails.put("agentName", agent.name());
            emit(observer, requestId, runId, sequence, "ROUTE_SELECTED", routeDetails, List.of());
            traces.emitMain(OrchestrationTraceChannel.KIND_STATUS, humanSoloHandoff(agent, effective), false);

            OrchestrationStep step = new OrchestrationStep("solo-1", agent.agentId(), planningQuery, List.of());
            OrchestrationPlan plan = planValidator.validate(new OrchestrationPlan(List.of(step)), List.of(agent));
            List<OrchestrationPlanStepView> steps = plan.steps().stream()
                    .map(item -> stepView(item, List.of(agent)))
                    .toList();
            Map<String, Object> planDetails = new LinkedHashMap<>();
            planDetails.put("attemptNo", 1);
            emit(observer, requestId, runId, sequence, "PLAN_CREATED", planDetails, steps);

            java.util.List<com.jd.genie.agent.dto.File> deliverableFiles =
                    java.util.Collections.synchronizedList(new java.util.ArrayList<>());
            Map<String, AgentTaskResult> results = serialService.execute(
                    user,
                    agentQuery,
                    conversationHistory,
                    UntrustedLocalContext.body(longTermMemory, conversationSummary),
                    plan.steps(),
                    new OrchestrationEventSink() {
                        @Override
                        public void emit(String eventType, OrchestrationStep current, AgentTaskResult result, Map<String, Object> details) {
                            emitStep(
                                    observer, requestId, runId, sequence, eventType, 1, current, result, details, steps
                            );
                        }

                        @Override
                        public void acceptDeliverables(java.util.List<com.jd.genie.agent.dto.File> files) {
                            if (files != null) {
                                deliverableFiles.addAll(files);
                            }
                        }
                    },
                    observer::isTerminal,
                    new LinkedHashMap<>(),
                    traces,
                    1,
                    observer,
                    fileSessionId
            );
            Map<String, String> successes = new LinkedHashMap<>();
            Map<String, String> failures = new LinkedHashMap<>();
            collectResults(results, successes, failures);
            completeSolo(
                    observer,
                    requestId,
                    runId,
                    sequence,
                    results,
                    successes,
                    failures,
                    traces,
                    deliverableFiles,
                    agent
            );
        } catch (RuntimeException error) {
            log.warn("Solo agent run failed: {}", error.getMessage(), error);
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
            String conversationHistory,
            List<AgentCapabilitySummary> candidates,
            MasterPersona masterPersona
    ) {
        String routingQuery = ChatAttachmentPrompt.withoutUploadedFileBodies(query);
        if ("ORCHESTRATED".equals(mode)) {
            return new RouteDecision(RouteDecision.Route.ORCHESTRATED, "FORCED_BY_REQUEST");
        }
        try {
            return modelPort.selectRoute(routingQuery, conversationSummary, conversationHistory, candidates, masterPersona);
        } catch (RuntimeException error) {
            log.warn("Router call failed, falling back to DIRECT: {}", error.getMessage(), error);
            return new RouteDecision(RouteDecision.Route.DIRECT, "ROUTER_FALLBACK");
        }
    }

    private void putTeamContext(Map<String, Object> details, MasterPersona persona, String teamId) {
        if (teamId != null && !teamId.isBlank()) {
            details.put("teamId", teamId);
        }
        if (persona != null && persona.present()) {
            details.put("masterAgentId", persona.agentId());
            details.put("masterAgentName", persona.displayName());
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
            String conversationHistory,
            String longTermMemory,
            String conversationSummary,
            List<OrchestrationPlanStepView> steps,
            Map<String, AgentTaskResult> results,
            Map<String, String> successes,
            Map<String, String> failures,
            String completionStatus,
            int attemptNo,
            OrchestrationTraceChannel traces,
            java.util.List<com.jd.genie.agent.dto.File> deliverableFiles,
            MasterPersona masterPersona
    ) {
        emit(observer, requestId, runId, sequence, "SUMMARY_STARTED", Map.of("attemptNo", attemptNo), List.of());
        traces.emitMain(attemptNo, OrchestrationTraceChannel.KIND_STATUS, "正在根据用户问题汇总各专家结论…", false);
        List<SummaryEvidence> evidence = toEvidence(steps, results, successes, failures);
        String fallback = fallbackAnswer(query, evidence);
        String answer;
        try {
            StringBuilder streamed = new StringBuilder();
            String overview = modelPort.summarize(
                    query, conversationHistory, longTermMemory, conversationSummary, evidence, masterPersona,
                    delta -> {
                        if (delta == null || delta.isEmpty() || observer.isTerminal()) {
                            return;
                        }
                        boolean append = streamed.length() > 0;
                        streamed.append(delta);
                        traces.emitMain(attemptNo, OrchestrationTraceChannel.KIND_OUTPUT, delta, append);
                        observer.onEventBestEffort(eventMapper.answerProgress(streamed.toString()));
                    });
            answer = overview == null || overview.isBlank()
                    ? (streamed.length() > 0 ? streamed.toString().trim() : fallback)
                    : overview.trim();
            emit(observer, requestId, runId, sequence, "SUMMARY_COMPLETED", Map.of("attemptNo", attemptNo), List.of());
            traces.emitMain(attemptNo, OrchestrationTraceChannel.KIND_STATUS, "汇总完成", false);
        } catch (RuntimeException error) {
            // The steps themselves succeeded, so the run keeps its status; only the wording degrades.
            log.warn("Summarization failed, emitting collected evidence instead: {}", error.getMessage(), error);
            answer = fallback;
            emit(observer, requestId, runId, sequence, "SUMMARY_FALLBACK", Map.of(
                    "attemptNo", attemptNo, "reasonCode", "SUMMARY_FAILED"
            ), List.of());
            traces.emitMain(attemptNo, OrchestrationTraceChannel.KIND_STATUS, "汇总失败，已使用已完成材料直出", false);
        }
        answer = DeliverableFiles.appendDownloadLinks(answer, deliverableFiles);
        GptProcessResult finalResponse = eventMapper.finalResponse(
                requestId,
                runId,
                sequence.incrementAndGet(),
                answer,
                completionStatus,
                DeliverableFiles.toFileList(deliverableFiles)
        );
        observer.onEvent(finalResponse);
        observer.onCompleted();
    }

    private void completeSolo(
            ConversationStreamObserver observer,
            String requestId,
            String runId,
            AtomicLong sequence,
            Map<String, AgentTaskResult> results,
            Map<String, String> successes,
            Map<String, String> failures,
            OrchestrationTraceChannel traces,
            java.util.List<com.jd.genie.agent.dto.File> deliverableFiles,
            AgentCapabilitySummary agent
    ) {
        String raw = successes.values().stream().findFirst().orElse("");
        if (raw == null || raw.isBlank()) {
            String error = failures.values().stream().findFirst().orElse("EXECUTION_ERROR");
            raw = agent.name() + " 这次没能形成可用结论（" + error + "）";
        }
        String answer = DeliverableFiles.appendDownloadLinks(raw, deliverableFiles);
        traces.emitMain(1, OrchestrationTraceChannel.KIND_STATUS, agent.name() + " 开始回复你", false);
        observer.onEventBestEffort(eventMapper.answerProgress(answer));
        String status = failures.isEmpty() ? "SUCCESS" : "PARTIAL";
        GptProcessResult finalResponse = eventMapper.finalResponse(
                requestId,
                runId,
                sequence.incrementAndGet(),
                answer,
                status,
                DeliverableFiles.toFileList(deliverableFiles)
        );
        observer.onEvent(finalResponse);
        observer.onCompleted();
    }

    private DispatchDecision fallbackDispatch(
            List<AgentCapabilitySummary> agents,
            List<TeamCapabilitySummary> teams
    ) {
        if (agents.size() == 1 && teams.isEmpty()) {
            AgentCapabilitySummary agent = agents.get(0);
            return DispatchDecision.agent(agent.agentId(), agent.name(), "ONLY_ONE_CANDIDATE");
        }
        if (teams.size() == 1 && agents.isEmpty()) {
            TeamCapabilitySummary team = teams.get(0);
            return DispatchDecision.team(team.teamId(), team.name(), "ONLY_ONE_TEAM");
        }
        throw new AgentBridgeException(MvpErrorCode.NO_SUITABLE_AGENT, "Unable to dispatch this request");
    }

    private String humanSoloHandoff(AgentCapabilitySummary agent, RouteDecision route) {
        String name = agent == null || agent.name() == null || agent.name().isBlank() ? "专家" : agent.name();
        String reason = route == null || route.reasonCode() == null ? "" : route.reasonCode();
        if ("AUTO_SINGLE_AGENT".equals(reason) || "SINGLE_CAPABILITY".equals(reason)
                || "MATCHED_SPECIALIST".equals(reason) || "ONLY_ONE_CANDIDATE".equals(reason)) {
            return "一位专家就能完成。已把对话交给「" + name + "」，主规划退出";
        }
        return "已把对话交给「" + name + "」";
    }

    private String humanDispatchStatus(RouteDecision route, MasterPersona persona, String teamId) {
        String reason = route == null || route.reasonCode() == null ? "" : route.reasonCode();
        if ("AUTO_TEAM".equals(reason) || "ONLY_ONE_TEAM".equals(reason) || "EXPLICIT_TEAM".equals(reason)
                || "MULTI_AGENT".equals(reason) || "MULTI_AGENT_DETECTED".equals(reason)) {
            if (teamId != null && !teamId.isBlank()) {
                String master = persona != null ? persona.displayName() : "团队主规划";
                return "需要团队协作。已把对话交给「" + master + "」接手，系统主规划退出";
            }
        }
        return humanRouteStatus(route);
    }

    private String humanRouteStatus(RouteDecision route) {
        if (route == null || route.route() == null) {
            return "已选择编排执行";
        }
        if (route.route() != RouteDecision.Route.ORCHESTRATED) {
            return "由主规划直接作答";
        }
        String reason = route.reasonCode() == null ? "" : route.reasonCode();
        return switch (reason) {
            case "RESOURCE_CREATION_REQUEST" -> "已选择编排执行，正在创建所需资源";
            case "FORCED_BY_REQUEST" -> "已选择编排执行，按你选择的协作模式推进";
            case "MULTI_STEP", "MULTI_AGENT", "MULTI_AGENT_DETECTED", "EXPLICIT_TEAM" ->
                    "这个问题需要多位专家一起完成";
            case "AUTO_TEAM", "ONLY_ONE_TEAM" -> "需要团队协作。已把对话交给所选团队的主规划，系统主规划退出";
            case "SOLO_AGENT" -> "已把对话交给所选专家";
            case "AUTO_SINGLE_AGENT", "SINGLE_CAPABILITY", "MATCHED_SPECIALIST", "ONLY_ONE_CANDIDATE" ->
                    "一位专家就能完成，主规划已退出";
            default -> "已选择编排执行";
        };
    }

    private String humanPlanStatus(List<OrchestrationPlanStepView> steps) {
        if (steps == null || steps.isEmpty()) {
            return "主规划正在安排任务";
        }
        if (steps.size() == 1) {
            String name = displayStepName(steps.get(0));
            return "主规划已安排 1 步，交给 " + name;
        }
        return "主规划已安排 " + steps.size() + " 步，将依次邀请专家协作";
    }

    private String displayStepName(OrchestrationPlanStepView step) {
        if (step == null) {
            return "专家";
        }
        if (step.agentName() != null && !step.agentName().isBlank()) {
            return step.agentName();
        }
        if (step.agentId() != null && !step.agentId().isBlank()) {
            return step.agentId();
        }
        return "专家";
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
        synchronized (sequence) {
            observer.onEvent(eventMapper.progress(requestId, runId, sequence.incrementAndGet(), eventType, details, steps));
        }
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

    /**
     * MAIN_ONLY has no DirectFallbackExecutor in production wiring, so a planner
     * that emits MAIN_ONLY (typical for greetings) fails immediately. Reassign
     * those steps to the first online candidate so Ensemble can finish.
     */
    static OrchestrationPlan assignCandidatesToMainOnlySteps(
            OrchestrationPlan plan,
            List<AgentCapabilitySummary> candidates
    ) {
        if (plan == null || plan.steps() == null || candidates == null || candidates.isEmpty()) {
            return plan;
        }
        String fallbackId = null;
        for (AgentCapabilitySummary candidate : candidates) {
            if (candidate != null && candidate.agentId() != null && !candidate.agentId().isBlank()) {
                fallbackId = candidate.agentId();
                break;
            }
        }
        if (fallbackId == null) {
            return plan;
        }
        List<OrchestrationStep> remapped = new java.util.ArrayList<>();
        boolean changed = false;
        for (OrchestrationStep step : plan.steps()) {
            if (step != null && step.mode() == StepMode.MAIN_ONLY) {
                remapped.add(new OrchestrationStep(
                        step.stepId(),
                        StepMode.SINGLE_AGENT,
                        step.objective(),
                        step.inputRefs(),
                        fallbackId,
                        List.of()
                ));
                changed = true;
            } else {
                remapped.add(step);
            }
        }
        return changed ? new OrchestrationPlan(remapped) : plan;
    }

    /**
     * Preserve the planner's semantic decision, while normalizing a selected hidden
     * builder to one first step.  This deliberately does not infer creation intent
     * from query keywords: that decision belongs to the planner model.
     */
    static OrchestrationPlan enforceSystemResourceStep(String query, OrchestrationPlan plan) {
        if (plan == null || plan.steps().stream().noneMatch(step -> SystemResourceBuilder.isSystemAgent(step.agentId()))) {
            return plan;
        }
        List<OrchestrationStep> retained = new java.util.ArrayList<>();
        java.util.Set<String> removedIds = new java.util.HashSet<>();
        for (OrchestrationStep step : plan.steps()) {
            if (SystemResourceBuilder.isSystemAgent(step.agentId())) {
                removedIds.add(step.stepId());
            } else {
                retained.add(step);
            }
        }
        List<OrchestrationStep> normalized = new java.util.ArrayList<>();
        OrchestrationStep plannedSystemStep = plan.steps().stream()
                .filter(step -> SystemResourceBuilder.isSystemAgent(step.agentId()))
                .findFirst().orElse(null);
        normalized.add(new OrchestrationStep(
                "system-resource-create", SystemResourceBuilder.AGENT_ID,
                plannedSystemStep == null || plannedSystemStep.objective() == null || plannedSystemStep.objective().isBlank()
                        ? "按用户意图创建 Agent 或 Team：输出结构化成员定义、职责、系统提示词及所需 Skill/MCP 绑定。"
                        : plannedSystemStep.objective(), List.of()
        ));
        for (OrchestrationStep step : retained) {
            List<String> refs = step.inputRefs().stream().filter(ref -> !removedIds.contains(ref)).toList();
            normalized.add(new OrchestrationStep(step.stepId(), step.mode(), step.objective(), refs, step.agentId(), step.subTasks()));
        }
        return new OrchestrationPlan(normalized);
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

    private List<SummaryEvidence> toEvidence(
            List<OrchestrationPlanStepView> steps,
            Map<String, AgentTaskResult> results,
            Map<String, String> successes,
            Map<String, String> failures
    ) {
        List<SummaryEvidence> evidence = new java.util.ArrayList<>();
        if (steps != null) {
            for (OrchestrationPlanStepView step : steps) {
                if (step == null || step.stepId() == null) {
                    continue;
                }
                AgentTaskResult result = results == null ? null : results.get(step.stepId());
                String output = result != null && result.status() == AgentTaskResult.Status.SUCCESS
                        ? result.output()
                        : successes == null ? null : successes.get(step.stepId());
                String errorCode = result != null && result.status() == AgentTaskResult.Status.FAILURE
                        ? result.errorCode()
                        : failures == null ? null : failures.get(step.stepId());
                evidence.add(new SummaryEvidence(
                        step.stepId(),
                        specialistLabel(step),
                        step.objective(),
                        output,
                        errorCode
                ));
            }
        }
        if (!evidence.isEmpty()) {
            return evidence;
        }
        if (successes != null) {
            successes.forEach((id, output) -> evidence.add(new SummaryEvidence(id, id, "", output, null)));
        }
        if (failures != null) {
            failures.forEach((id, code) -> evidence.add(new SummaryEvidence(id, id, "", null, code)));
        }
        return evidence;
    }

    private String specialistLabel(OrchestrationPlanStepView step) {
        if (step.subTasks() != null && !step.subTasks().isEmpty()) {
            String names = step.subTasks().stream()
                    .map(sub -> sub.agentName() == null || sub.agentName().isBlank() ? sub.agentId() : sub.agentName())
                    .filter(name -> name != null && !name.isBlank())
                    .collect(Collectors.joining("、"));
            if (!names.isBlank()) {
                return names;
            }
        }
        if (step.agentName() != null && !step.agentName().isBlank()) {
            return step.agentName();
        }
        return step.agentId() == null || step.agentId().isBlank() ? step.stepId() : step.agentId();
    }

    private String fallbackAnswer(String query, List<SummaryEvidence> evidence) {
        StringBuilder sb = new StringBuilder();
        sb.append("针对问题：").append(query == null ? "" : query.trim()).append("\n\n");
        boolean anySuccess = evidence.stream().anyMatch(item -> !item.failed() && item.output() != null && !item.output().isBlank());
        if (anySuccess) {
            sb.append("已收集到的材料：\n");
            for (SummaryEvidence item : evidence) {
                if (item.failed() || item.output() == null || item.output().isBlank()) {
                    continue;
                }
                sb.append("- ").append(item.displayName());
                if (item.objective() != null && !item.objective().isBlank()) {
                    sb.append("（").append(item.objective().trim()).append("）");
                }
                sb.append("：\n").append(item.output().trim()).append("\n\n");
            }
        }
        List<SummaryEvidence> failed = evidence.stream().filter(SummaryEvidence::failed).toList();
        if (!failed.isEmpty()) {
            sb.append("以下工作未能完成：\n");
            for (SummaryEvidence item : failed) {
                sb.append("- ").append(item.displayName()).append('\n');
            }
        }
        if (!anySuccess && failed.isEmpty()) {
            sb.append("暂无可用材料。");
        }
        return sb.toString().trim();
    }
}
