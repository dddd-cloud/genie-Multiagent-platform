package com.jd.genie.platform.phase2.runtime.orchestration;

import com.jd.genie.agent.agent.AgentContext;
import com.jd.genie.agent.printer.Printer;
import com.jd.genie.agent.tool.ToolCollection;
import com.jd.genie.platform.agentbridge.AgentBridgeException;
import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2.runtime.agent.AgentTaskResult;
import com.jd.genie.platform.phase2.runtime.agent.ConfiguredAgentExecutor;
import com.jd.genie.platform.phase2.runtime.agent.ConfiguredAgentPrinter;
import com.jd.genie.platform.phase2.runtime.plan.OrchestrationStep;
import com.jd.genie.platform.phase2contract.dto.AgentRuntimeProfile;
import com.jd.genie.platform.phase2contract.port.AgentRuntimeCatalogPort;
import com.jd.genie.platform.phase2contract.port.RuntimeToolCollectionPort;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

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
                        cancellationRequested
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
            BooleanSupplier cancellationRequested
    ) {
        if (!runningStepId.compareAndSet(null, step.stepId())) {
            throw new AgentBridgeException(MvpErrorCode.INTERNAL_ERROR, "More than one orchestration step is running");
        }
        ConfiguredAgentPrinter printer = new ConfiguredAgentPrinter();
        try {
            AgentRuntimeProfile profile = catalogPort.loadOnlineProfile(user, step.agentId());
            String signature = resultSignature(step, inputs, profile.agentVersion());
            AgentTaskResult reused = reusableResults.get(signature);
            if (reused != null) {
                events.emit("STEP_COMPLETED", step, reused, Map.of("reasonCode", "REUSED"));
                return reused;
            }
            AgentContext context = AgentContext.builder()
                    .requestId(step.stepId())
                    .query(query)
                    .task(step.objective())
                    .basePrompt(step.objective() + "\nReferenced results:\n" + inputs)
                    .build();
            ToolCollection tools = toolCollectionPort.build(user, profile, context);
            context.setToolCollection(tools);
            if (cancellationRequested.getAsBoolean()) {
                throw new AgentBridgeException(MvpErrorCode.CLIENT_DISCONNECTED, "Orchestration cancelled before Agent launch");
            }
            events.emit("STEP_STARTED", step, null, Map.of("agentId", step.agentId()));
            AgentTaskResult result = executor.execute(context, profile, printer, maxAgentSteps);
            if (result.status() == AgentTaskResult.Status.SUCCESS) {
                reusableResults.put(signature, result);
            }
            events.emit(result.status() == AgentTaskResult.Status.SUCCESS ? "STEP_COMPLETED" : "STEP_FAILED", step, result, Map.of());
            return result;
        } catch (AgentBridgeException error) {
            if (error.getErrorCode() == MvpErrorCode.CLIENT_DISCONNECTED) {
                throw error;
            }
            String errorCode = orchestrationErrorCode(error.getErrorCode());
            AgentTaskResult failure = AgentTaskResult.failure(errorCode, errorCode.equals("TOOL_TIMEOUT") || errorCode.equals("TOOL_UNAVAILABLE") || errorCode.equals("EXECUTION_ERROR"));
            events.emit("STEP_FAILED", step, failure, Map.of("errorCode", errorCode));
            return failure;
        } catch (Exception error) {
            AgentTaskResult failure = AgentTaskResult.failure("EXECUTION_ERROR", true);
            events.emit("STEP_FAILED", step, failure, Map.of("errorCode", "EXECUTION_ERROR"));
            return failure;
        } finally {
            printer.close();
            runningStepId.set(null);
        }
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
