package com.jd.genie.platform.phase2.runtime.orchestration;

import com.jd.genie.agent.agent.AgentContext;
import com.jd.genie.agent.dto.File;
import com.jd.genie.agent.tool.ToolCollection;
import com.jd.genie.agent.util.DateUtil;
import com.jd.genie.platform.agentbridge.AgentBridgeException;
import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2.runtime.agent.AgentTaskResult;
import com.jd.genie.platform.phase2.runtime.agent.ConfiguredAgentExecutor;
import com.jd.genie.platform.phase2.runtime.agent.ConfiguredAgentPrinter;
import com.jd.genie.platform.phase2.runtime.plan.OrchestrationStep;
import com.jd.genie.platform.phase2.runtime.trace.OrchestrationTraceChannel;
import com.jd.genie.platform.phase2contract.dto.AgentRuntimeProfile;
import com.jd.genie.platform.phase2contract.port.AgentRuntimeCatalogPort;
import com.jd.genie.platform.phase2contract.port.RuntimeToolCollectionPort;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

@Slf4j
public final class SerialOrchestrationService {
    private final AgentRuntimeCatalogPort catalogPort;
    private final RuntimeToolCollectionPort toolCollectionPort;
    private final ConfiguredAgentExecutor executor;
    private final ThreadLocal<AtomicReference<String>> requestRunningStep = new ThreadLocal<>();
    private final int maxAgentSteps;

    public SerialOrchestrationService(
            AgentRuntimeCatalogPort catalogPort,
            RuntimeToolCollectionPort toolCollectionPort,
            ConfiguredAgentExecutor executor,
            int maxAgentSteps
    ) {
        this.catalogPort = catalogPort;
        this.toolCollectionPort = toolCollectionPort;
        this.executor = executor;
        this.maxAgentSteps = maxAgentSteps;
    }

    public Map<String, AgentTaskResult> execute(
            CurrentUser user,
            String query,
            Iterable<OrchestrationStep> steps,
            OrchestrationEventSink events
    ) {
        return execute(user, query, steps, events, () -> false);
    }

    public Map<String, AgentTaskResult> execute(
            CurrentUser user,
            String query,
            Iterable<OrchestrationStep> steps,
            OrchestrationEventSink events,
            BooleanSupplier cancellationRequested
    ) {
        return execute(user, query, steps, events, cancellationRequested, new LinkedHashMap<>());
    }

    public Map<String, AgentTaskResult> execute(
            CurrentUser user,
            String query,
            Iterable<OrchestrationStep> steps,
            OrchestrationEventSink events,
            BooleanSupplier cancellationRequested,
            Map<String, AgentTaskResult> reusableResults
    ) {
        return execute(user, query, steps, events, cancellationRequested, reusableResults, null, 1);
    }

    public Map<String, AgentTaskResult> execute(
            CurrentUser user,
            String query,
            Iterable<OrchestrationStep> steps,
            OrchestrationEventSink events,
            BooleanSupplier cancellationRequested,
            Map<String, AgentTaskResult> reusableResults,
            OrchestrationTraceChannel traceChannel,
            int attemptNo
    ) {
        AtomicReference<String> runningStepId = new AtomicReference<>();
        requestRunningStep.set(runningStepId);
        try {
            Map<String, AgentTaskResult> results = new LinkedHashMap<>();
            boolean blocked = false;
            for (OrchestrationStep step : steps) {
                if (cancellationRequested.getAsBoolean()) {
                    throw new AgentBridgeException(MvpErrorCode.CLIENT_DISCONNECTED, "Orchestration cancelled before next step");
                }
                if (blocked) {
                    AgentTaskResult skipped = AgentTaskResult.failure("EXECUTION_ERROR", false);
                    results.put(step.stepId(), skipped);
                    events.emit("STEP_SKIPPED", step, skipped, Map.of("reasonCode", "PREVIOUS_STEP_FAILED"));
                    continue;
                }
                Map<String, String> inputs = referencedSuccessfulOutputs(step, results);
                if (inputs == null) {
                    AgentTaskResult skipped = AgentTaskResult.failure("EXECUTION_ERROR", false);
                    results.put(step.stepId(), skipped);
                    events.emit("STEP_SKIPPED", step, skipped, Map.of("reasonCode", "DEPENDENCY_NOT_SUCCEEDED"));
                    blocked = true;
                    continue;
                }
                AgentTaskResult result = executeStep(
                        user,
                        query,
                        step,
                        inputs,
                        reusableResults,
                        events,
                        runningStepId,
                        cancellationRequested,
                        traceChannel,
                        attemptNo
                );
                results.put(step.stepId(), result);
                blocked = result.status() == AgentTaskResult.Status.FAILURE;
            }
            return Map.copyOf(results);
        } finally {
            requestRunningStep.remove();
        }
    }

    private AgentTaskResult executeStep(
            CurrentUser user,
            String query,
            OrchestrationStep step,
            Map<String, String> inputs,
            Map<String, AgentTaskResult> reusableResults,
            OrchestrationEventSink events,
            AtomicReference<String> runningStepId,
            BooleanSupplier cancellationRequested,
            OrchestrationTraceChannel traceChannel,
            int attemptNo
    ) {
        if (!runningStepId.compareAndSet(null, step.stepId())) {
            throw new AgentBridgeException(MvpErrorCode.INTERNAL_ERROR, "More than one orchestration step is running");
        }
        ConfiguredAgentPrinter printer = new ConfiguredAgentPrinter();
        try {
            AgentRuntimeProfile profile = catalogPort.loadOnlineProfile(user, step.agentId());
            String agentName = profile.name() == null || profile.name().isBlank() ? step.agentId() : profile.name();
            printer = new ConfiguredAgentPrinter(
                    traceChannel, attemptNo, step.stepId(), step.agentId(), agentName);
            String signature = resultSignature(step, inputs, profile.agentVersion());
            AgentTaskResult reused = reusableResults.get(signature);
            if (reused != null) {
                events.emit("STEP_COMPLETED", step, reused, Map.of(
                        "reasonCode", "REUSED",
                        "agentName", agentName
                ));
                if (traceChannel != null && reused.status() == AgentTaskResult.Status.SUCCESS) {
                    traceChannel.emitStep(attemptNo, step.stepId(), step.agentId(), agentName,
                            OrchestrationTraceChannel.KIND_OUTPUT,
                            reused.output() == null ? "" : reused.output(),
                            false);
                }
                return reused;
            }
            List<File> emptyFiles = new ArrayList<>();
            String objective = step.objective() == null ? "" : step.objective().trim();
            // Sub-agents must run the step objective, NOT the parent orchestration query.
            // Passing the full user request makes agents invent "only one agent available" answers.
            String stepQuery = buildStepQuery(
                    agentName,
                    profile.description(),
                    objective,
                    inputs
            );
            AgentContext context = AgentContext.builder()
                    .requestId(step.stepId())
                    .sessionId(step.stepId())
                    .query(stepQuery)
                    .task(objective)
                    .basePrompt(stepQuery)
                    .dateInfo(DateUtil.CurrentDateInfo())
                    .productFiles(emptyFiles)
                    .taskProductFiles(emptyFiles)
                    .isStream(false)
                    .templateType("empty")
                    .build();
            ToolCollection tools = toolCollectionPort.build(user, profile, context);
            context.setToolCollection(tools);
            if (cancellationRequested.getAsBoolean()) {
                throw new AgentBridgeException(MvpErrorCode.CLIENT_DISCONNECTED, "Orchestration cancelled before Agent launch");
            }
            events.emit("STEP_STARTED", step, null, Map.of(
                    "agentId", step.agentId(),
                    "agentName", agentName
            ));
            if (traceChannel != null) {
                traceChannel.emitStep(attemptNo, step.stepId(), step.agentId(), agentName,
                        OrchestrationTraceChannel.KIND_STATUS,
                        "开始执行：" + objective,
                        false);
            }
            AgentTaskResult result = executor.execute(context, profile, printer, maxAgentSteps);
            if (result.status() == AgentTaskResult.Status.SUCCESS) {
                reusableResults.put(signature, result);
            }
            events.emit(
                    result.status() == AgentTaskResult.Status.SUCCESS ? "STEP_COMPLETED" : "STEP_FAILED",
                    step,
                    result,
                    Map.of("agentName", agentName)
            );
            if (traceChannel != null) {
                if (result.status() == AgentTaskResult.Status.SUCCESS) {
                    traceChannel.emitStep(attemptNo, step.stepId(), step.agentId(), agentName,
                            OrchestrationTraceChannel.KIND_OUTPUT,
                            result.output() == null ? "" : result.output(),
                            false);
                } else {
                    traceChannel.emitStep(attemptNo, step.stepId(), step.agentId(), agentName,
                            OrchestrationTraceChannel.KIND_ERROR,
                            result.errorCode() == null ? "EXECUTION_ERROR" : result.errorCode(),
                            false);
                }
            }
            return result;
        } catch (AgentBridgeException error) {
            if (error.getErrorCode() == MvpErrorCode.CLIENT_DISCONNECTED) {
                throw error;
            }
            log.warn("Orchestration step failed agentId={} stepId={} code={}",
                    step.agentId(), step.stepId(), error.getErrorCode(), error);
            String errorCode = orchestrationErrorCode(error.getErrorCode());
            AgentTaskResult failure = AgentTaskResult.failure(errorCode, errorCode.equals("TOOL_TIMEOUT") || errorCode.equals("TOOL_UNAVAILABLE") || errorCode.equals("EXECUTION_ERROR"));
            events.emit("STEP_FAILED", step, failure, Map.of("errorCode", errorCode));
            if (traceChannel != null) {
                traceChannel.emitStep(attemptNo, step.stepId(), step.agentId(), step.agentId(),
                        OrchestrationTraceChannel.KIND_ERROR, errorCode, false);
            }
            return failure;
        } catch (Exception error) {
            log.error("Orchestration step crashed agentId={} stepId={}", step.agentId(), step.stepId(), error);
            AgentTaskResult failure = AgentTaskResult.failure("EXECUTION_ERROR", true);
            events.emit("STEP_FAILED", step, failure, Map.of("errorCode", "EXECUTION_ERROR"));
            if (traceChannel != null) {
                traceChannel.emitStep(attemptNo, step.stepId(), step.agentId(), step.agentId(),
                        OrchestrationTraceChannel.KIND_ERROR, "EXECUTION_ERROR", false);
            }
            return failure;
        } finally {
            printer.close();
            runningStepId.set(null);
        }
    }

    private String buildStepQuery(
            String agentName,
            String agentDescription,
            String objective,
            Map<String, String> inputs
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是子 Agent「").append(agentName == null ? "" : agentName).append("」。\n");
        if (agentDescription != null && !agentDescription.isBlank()) {
            sb.append("你的角色设定：").append(agentDescription.trim()).append('\n');
        }
        sb.append("请只完成下面的步骤目标，不要回答编排总问题，也不要讨论还有哪些 Agent 可用。\n");
        sb.append("请用你自己独特的视角和措辞作答；禁止与其他 Agent 输出相同或高度雷同的句子。\n");
        sb.append("步骤目标：\n").append(objective == null ? "" : objective);
        if (inputs != null && !inputs.isEmpty()) {
            sb.append("\n\n可参考的前置步骤结果：\n");
            for (Map.Entry<String, String> entry : inputs.entrySet()) {
                sb.append("- ").append(entry.getKey()).append(": ")
                        .append(entry.getValue() == null ? "" : entry.getValue())
                        .append('\n');
            }
            sb.append("若本步骤是汇总，请综合上述结果写成新的段落，不要原样复述其中某一条。\n");
        }
        sb.append("\n直接给出该步骤的最终答案。");
        return sb.toString();
    }

    private Map<String, String> referencedSuccessfulOutputs(
            OrchestrationStep step,
            Map<String, AgentTaskResult> results
    ) {
        Map<String, String> inputs = new LinkedHashMap<>();
        for (String ref : step.inputRefs()) {
            AgentTaskResult result = results.get(ref);
            if (result == null || result.status() != AgentTaskResult.Status.SUCCESS) {
                return null;
            }
            inputs.put(ref, result.output());
        }
        return Map.copyOf(inputs);
    }

    public String runningStepId() {
        AtomicReference<String> runningStepId = requestRunningStep.get();
        return runningStepId == null ? null : runningStepId.get();
    }

    private String resultSignature(OrchestrationStep step, Map<String, String> inputs, long agentVersion) {
        String source = step.objective().trim().replaceAll("\\s+", " ")
                + "\u0000" + step.agentId()
                + "\u0000" + agentVersion
                + "\u0000" + inputs.entrySet().stream()
                        .map(entry -> entry.getKey() + "\u0000" + sha256(entry.getValue()))
                        .collect(java.util.stream.Collectors.joining("\u0001"));
        return sha256(source);
    }

    private String sha256(String source) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }

    private String orchestrationErrorCode(MvpErrorCode errorCode) {
        return switch (errorCode) {
            case TOOL_NOT_BOUND -> "TOOL_PERMISSION_DENIED";
            case TOOL_TIMEOUT -> "TOOL_TIMEOUT";
            case MCP_UNAVAILABLE -> "TOOL_UNAVAILABLE";
            case TOOL_INVALID_INPUT, TOOL_INVALID_RESPONSE -> "TOOL_INVALID_RESPONSE";
            case AGENT_OFFLINE -> "AGENT_OFFLINE";
            case AGENT_INVALID_RESULT -> "AGENT_INVALID_RESULT";
            case CONTEXT_BUDGET_EXCEEDED -> "CONTEXT_BUDGET_EXCEEDED";
            case VALIDATION_ERROR -> "INVALID_INPUT";
            default -> "EXECUTION_ERROR";
        };
    }
}
