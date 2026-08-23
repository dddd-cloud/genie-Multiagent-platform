package com.jd.genie.platform.phase2.runtime.orchestration;

import com.jd.genie.agent.agent.AgentContext;
import com.jd.genie.agent.dto.File;
import com.jd.genie.agent.tool.BaseTool;
import com.jd.genie.agent.tool.ToolCollection;
import com.jd.genie.agent.util.DateUtil;
import com.jd.genie.platform.agentbridge.AgentBridgeException;
import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2.runtime.agent.AgentTaskResult;
import com.jd.genie.platform.phase2.runtime.agent.ConfiguredAgentExecutor;
import com.jd.genie.platform.phase2.runtime.agent.ConfiguredAgentPrinter;
import com.jd.genie.platform.phase2.runtime.plan.OrchestrationStep;
import com.jd.genie.platform.phase2.runtime.plan.OrchestrationSubTask;
import com.jd.genie.platform.phase2.runtime.context.BrowserWorkspaceContextPolicy;
import com.jd.genie.platform.phase2.runtime.resource.SystemResourceBuilder;
import com.jd.genie.platform.phase2.runtime.trace.OrchestrationTraceChannel;
import com.jd.genie.platform.phase2.skillruntime.execution.BrowserWorkspacePythonToolFactory;
import com.jd.genie.platform.phase2contract.dto.AgentRuntimeProfile;
import com.jd.genie.platform.phase2contract.port.AgentRuntimeCatalogPort;
import com.jd.genie.platform.phase2contract.port.RuntimeToolCollectionPort;
import com.jd.genie.platform.phase2contract.port.SkillRuntimePort;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

@Slf4j
public final class SerialOrchestrationService {
    private final AgentRuntimeCatalogPort catalogPort;
    private final RuntimeToolCollectionPort toolCollectionPort;
    private final SkillRuntimePort skillRuntimePort;
    private final ConfiguredAgentExecutor executor;
    private final OrchestrationModelPort modelPort;
    private final DirectFallbackExecutor directFallbackExecutor;
    private final SystemResourceBuilder systemResourceBuilder;
    private final BrowserWorkspacePythonToolFactory browserWorkspacePythonToolFactory;
    private final ThreadLocal<AtomicReference<String>> requestRunningStep = new ThreadLocal<>();
    private final int maxAgentSteps;

    public SerialOrchestrationService(
            AgentRuntimeCatalogPort catalogPort,
            RuntimeToolCollectionPort toolCollectionPort,
            ConfiguredAgentExecutor executor,
            int maxAgentSteps
    ) {
        this(catalogPort, toolCollectionPort, null, executor, maxAgentSteps, null);
    }

    public SerialOrchestrationService(
            AgentRuntimeCatalogPort catalogPort,
            RuntimeToolCollectionPort toolCollectionPort,
            SkillRuntimePort skillRuntimePort,
            ConfiguredAgentExecutor executor,
            int maxAgentSteps
    ) {
        this(catalogPort, toolCollectionPort, skillRuntimePort, executor, maxAgentSteps, null);
    }

    public SerialOrchestrationService(
            AgentRuntimeCatalogPort catalogPort,
            RuntimeToolCollectionPort toolCollectionPort,
            SkillRuntimePort skillRuntimePort,
            ConfiguredAgentExecutor executor,
            int maxAgentSteps,
            OrchestrationModelPort modelPort
    ) {
        this(catalogPort, toolCollectionPort, skillRuntimePort, executor, maxAgentSteps, modelPort, null, null);
    }

    public SerialOrchestrationService(
            AgentRuntimeCatalogPort catalogPort,
            RuntimeToolCollectionPort toolCollectionPort,
            SkillRuntimePort skillRuntimePort,
            ConfiguredAgentExecutor executor,
            int maxAgentSteps,
            OrchestrationModelPort modelPort,
            DirectFallbackExecutor directFallbackExecutor
    ) {
        this(catalogPort, toolCollectionPort, skillRuntimePort, executor, maxAgentSteps, modelPort, directFallbackExecutor, null);
    }

    public SerialOrchestrationService(
            AgentRuntimeCatalogPort catalogPort,
            RuntimeToolCollectionPort toolCollectionPort,
            SkillRuntimePort skillRuntimePort,
            ConfiguredAgentExecutor executor,
            int maxAgentSteps,
            OrchestrationModelPort modelPort,
            DirectFallbackExecutor directFallbackExecutor,
            SystemResourceBuilder systemResourceBuilder
    ) {
        this(catalogPort, toolCollectionPort, skillRuntimePort, executor, maxAgentSteps, modelPort,
                directFallbackExecutor, systemResourceBuilder, null);
    }

    public SerialOrchestrationService(
            AgentRuntimeCatalogPort catalogPort,
            RuntimeToolCollectionPort toolCollectionPort,
            SkillRuntimePort skillRuntimePort,
            ConfiguredAgentExecutor executor,
            int maxAgentSteps,
            OrchestrationModelPort modelPort,
            DirectFallbackExecutor directFallbackExecutor,
            SystemResourceBuilder systemResourceBuilder,
            BrowserWorkspacePythonToolFactory browserWorkspacePythonToolFactory
    ) {
        this.catalogPort = catalogPort;
        this.toolCollectionPort = toolCollectionPort;
        this.skillRuntimePort = skillRuntimePort;
        this.executor = executor;
        this.maxAgentSteps = maxAgentSteps;
        this.modelPort = modelPort;
        this.directFallbackExecutor = directFallbackExecutor;
        this.systemResourceBuilder = systemResourceBuilder;
        this.browserWorkspacePythonToolFactory = browserWorkspacePythonToolFactory;
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
        return execute(user, query, "", steps, events, cancellationRequested, reusableResults, null, 1);
    }

    public Map<String, AgentTaskResult> execute(
            CurrentUser user,
            String query,
            String longTermMemory,
            Iterable<OrchestrationStep> steps,
            OrchestrationEventSink events,
            BooleanSupplier cancellationRequested,
            Map<String, AgentTaskResult> reusableResults,
            OrchestrationTraceChannel traceChannel,
            int attemptNo
    ) {
        return execute(user, query, "", longTermMemory, steps, events, cancellationRequested, reusableResults, traceChannel, attemptNo, null);
    }

    public Map<String, AgentTaskResult> execute(
            CurrentUser user,
            String query,
            String longTermMemory,
            Iterable<OrchestrationStep> steps,
            OrchestrationEventSink events,
            BooleanSupplier cancellationRequested,
            Map<String, AgentTaskResult> reusableResults,
            OrchestrationTraceChannel traceChannel,
            int attemptNo,
            com.jd.genie.platform.agentbridge.ConversationStreamObserver observer
    ) {
        return execute(user, query, "", longTermMemory, steps, events, cancellationRequested, reusableResults, traceChannel, attemptNo, observer, null);
    }

    public Map<String, AgentTaskResult> execute(
            CurrentUser user,
            String query,
            String conversationHistory,
            String longTermMemory,
            Iterable<OrchestrationStep> steps,
            OrchestrationEventSink events,
            BooleanSupplier cancellationRequested,
            Map<String, AgentTaskResult> reusableResults,
            OrchestrationTraceChannel traceChannel,
            int attemptNo,
            com.jd.genie.platform.agentbridge.ConversationStreamObserver observer
    ) {
        return execute(user, query, conversationHistory, longTermMemory, steps, events, cancellationRequested,
                reusableResults, traceChannel, attemptNo, observer, null);
    }

    public Map<String, AgentTaskResult> execute(
            CurrentUser user,
            String query,
            String conversationHistory,
            String longTermMemory,
            Iterable<OrchestrationStep> steps,
            OrchestrationEventSink events,
            BooleanSupplier cancellationRequested,
            Map<String, AgentTaskResult> reusableResults,
            OrchestrationTraceChannel traceChannel,
            int attemptNo,
            com.jd.genie.platform.agentbridge.ConversationStreamObserver observer,
            String fileSessionId
    ) {
        return execute(user, query, conversationHistory, longTermMemory, steps, events, cancellationRequested,
                reusableResults, traceChannel, attemptNo, observer, fileSessionId, false);
    }

    /**
     * @param finalAnswer true when this step's own output is delivered to the user
     *                     verbatim with no later synthesis pass (the solo-agent seam) —
     *                     the step agent is told to write a complete final answer
     *                     instead of fact-finding notes for a downstream summarizer.
     */
    public Map<String, AgentTaskResult> execute(
            CurrentUser user,
            String query,
            String conversationHistory,
            String longTermMemory,
            Iterable<OrchestrationStep> steps,
            OrchestrationEventSink events,
            BooleanSupplier cancellationRequested,
            Map<String, AgentTaskResult> reusableResults,
            OrchestrationTraceChannel traceChannel,
            int attemptNo,
            com.jd.genie.platform.agentbridge.ConversationStreamObserver observer,
            String fileSessionId,
            boolean finalAnswer
    ) {
        AtomicReference<String> runningStepId = new AtomicReference<>();
        requestRunningStep.set(runningStepId);
        String fileScope = resolveFileScope(fileSessionId);
        try {
            Map<String, AgentTaskResult> results = new LinkedHashMap<>();
            List<File> deliverableFiles = Collections.synchronizedList(new ArrayList<>());
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
                AgentTaskResult result = executeCurrentStep(
                        user,
                        query,
                        conversationHistory,
                        longTermMemory,
                        step,
                        inputs,
                        reusableResults,
                        events,
                        runningStepId,
                        cancellationRequested,
                        traceChannel,
                        attemptNo,
                        observer,
                        deliverableFiles,
                        fileScope,
                        finalAnswer
                );
                results.put(step.stepId(), result);
                blocked = result.status() == AgentTaskResult.Status.FAILURE;
            }
            events.acceptDeliverables(List.copyOf(deliverableFiles));
            return Map.copyOf(results);
        } finally {
            requestRunningStep.remove();
        }
    }

    private AgentTaskResult executeCurrentStep(
            CurrentUser user,
            String query,
            String conversationHistory,
            String longTermMemory,
            OrchestrationStep step,
            Map<String, String> inputs,
            Map<String, AgentTaskResult> reusableResults,
            OrchestrationEventSink events,
            AtomicReference<String> runningStepId,
            BooleanSupplier cancellationRequested,
            OrchestrationTraceChannel traceChannel,
            int attemptNo,
            com.jd.genie.platform.agentbridge.ConversationStreamObserver observer,
            List<File> deliverableFiles,
            String fileScope,
            boolean finalAnswer
    ) {
        return switch (step.mode()) {
            case SINGLE_AGENT -> executeSingleStepWithReview(
                    user, query, conversationHistory, longTermMemory, step, inputs, reusableResults, events, runningStepId,
                    cancellationRequested, traceChannel, attemptNo, observer, deliverableFiles, fileScope, finalAnswer
            );
            case PARALLEL_AGENTS -> executeParallelStep(
                    user,
                    query,
                    conversationHistory,
                    longTermMemory,
                    step,
                    inputs,
                    events,
                    runningStepId,
                    cancellationRequested,
                    traceChannel,
                    attemptNo,
                    observer,
                    deliverableFiles,
                    fileScope
            );
            case MAIN_ONLY -> executeMainOnlyStep(
                    user, longTermMemory, step, inputs, events, runningStepId,
                    cancellationRequested, traceChannel, attemptNo, observer
            );
        };
    }

    /**
     * MAIN_ONLY is executed by Main itself through the frozen non-orchestrated DIRECT
     * execution seam (the same seam used by Main Fallback). It never re-routes, never
     * creates a business Agent, never recurses into fallback, and retries at most once.
     */
    private AgentTaskResult executeMainOnlyStep(
            CurrentUser user,
            String longTermMemory,
            OrchestrationStep step,
            Map<String, String> inputs,
            OrchestrationEventSink events,
            AtomicReference<String> runningStepId,
            BooleanSupplier cancellationRequested,
            OrchestrationTraceChannel traceChannel,
            int attemptNo,
            com.jd.genie.platform.agentbridge.ConversationStreamObserver observer
    ) {
        if (!runningStepId.compareAndSet(null, step.stepId())) {
            throw new AgentBridgeException(MvpErrorCode.INTERNAL_ERROR, "More than one orchestration step is running");
        }
        try {
            if (directFallbackExecutor == null) {
                AgentTaskResult failure = AgentTaskResult.failure("EXECUTION_ERROR", false);
                events.emit("STEP_FAILED", step, failure, Map.of(
                        "errorCode", "EXECUTION_ERROR",
                        "reasonCode", "MAIN_SEAM_UNAVAILABLE"
                ));
                return failure;
            }
            events.emit("STEP_STARTED", step, null, Map.of("stepMode", "MAIN_ONLY"));
            if (traceChannel != null) {
                traceChannel.emitMain(attemptNo, OrchestrationTraceChannel.KIND_STATUS,
                        "开始执行：" + (step.objective() == null ? "" : step.objective()), false);
            }
            AgentTaskResult result = executeMainOnce(step, cancellationRequested, observer);
            if (result.status() == AgentTaskResult.Status.SUCCESS) {
                events.emit("STEP_COMPLETED", step, result, Map.of("stepMode", "MAIN_ONLY"));
                return result;
            }
            if (result.retryable()) {
                events.emit("STEP_RETRY_STARTED", step, result, Map.of("retryNo", 1));
                result = executeMainOnce(step, cancellationRequested, observer);
                if (result.status() == AgentTaskResult.Status.SUCCESS) {
                    events.emit("STEP_COMPLETED", step, result, Map.of("stepMode", "MAIN_ONLY"));
                    return result;
                }
            }
            String errorCode = result.errorCode() == null || result.errorCode().isBlank()
                    ? "EXECUTION_ERROR"
                    : result.errorCode();
            events.emit("STEP_FAILED", step, result, Map.of(
                    "errorCode", errorCode,
                    "reasonCode", "MAIN_ONLY_FAILED"
            ));
            return AgentTaskResult.failure(errorCode, false);
        } finally {
            runningStepId.set(null);
        }
    }

    private AgentTaskResult executeMainOnce(
            OrchestrationStep step,
            BooleanSupplier cancellationRequested,
            com.jd.genie.platform.agentbridge.ConversationStreamObserver observer
    ) {
        if (cancellationRequested.getAsBoolean()) {
            throw new AgentBridgeException(MvpErrorCode.CLIENT_DISCONNECTED, "Orchestration cancelled before MAIN_ONLY step");
        }
        com.jd.genie.platform.agentbridge.CancellableAgentCall cancellableCall =
                new com.jd.genie.platform.agentbridge.CancellableAgentCall();
        return directFallbackExecutor.executeFallback(step.objective(), observer, cancellableCall);
    }

    private AgentTaskResult executeSingleStepWithReview(
            CurrentUser user,
            String query,
            String conversationHistory,
            String longTermMemory,
            OrchestrationStep step,
            Map<String, String> inputs,
            Map<String, AgentTaskResult> reusableResults,
            OrchestrationEventSink events,
            AtomicReference<String> runningStepId,
            BooleanSupplier cancellationRequested,
            OrchestrationTraceChannel traceChannel,
            int attemptNo,
            com.jd.genie.platform.agentbridge.ConversationStreamObserver observer,
            List<File> deliverableFiles,
            String fileScope,
            boolean finalAnswer
    ) {
        AgentTaskResult initial = executeStep(user, query, conversationHistory, longTermMemory, step, step.stepId(), step.agentId(), step.objective(),
                inputs, reusableResults, events, runningStepId, cancellationRequested, traceChannel, attemptNo, false, observer, deliverableFiles, fileScope, finalAnswer);
        if (modelPort == null) {
            return initial;
        }
        OrchestrationModelPort.ReviewDecision decision = review(step, initial, 0, events);
        if (decision == OrchestrationModelPort.ReviewDecision.COMPLETE) {
            if (initial.status() == AgentTaskResult.Status.SUCCESS) {
                return initial;
            }
            AgentTaskResult failed = stepFailure(initial);
            events.emit("STEP_FAILED", step, failed, Map.of(
                    "errorCode", failed.errorCode(),
                    "reasonCode", "REVIEW_REJECTED"
            ));
            return failed;
        }
        if (decision == OrchestrationModelPort.ReviewDecision.RETRY && retryNoEligible(0)) {
            events.emit("STEP_RETRY_STARTED", step, initial, Map.of("retryNo", 1));
            AgentTaskResult retried = executeStep(user, query, conversationHistory, longTermMemory, step, step.stepId(), step.agentId(), step.objective(),
                    inputs, new LinkedHashMap<>(), events, runningStepId, cancellationRequested, traceChannel, attemptNo, false, observer, deliverableFiles, fileScope, finalAnswer);
            OrchestrationModelPort.ReviewDecision retryDecision = review(step, retried, 1, events);
            if (retryDecision == OrchestrationModelPort.ReviewDecision.COMPLETE
                    && retried.status() == AgentTaskResult.Status.SUCCESS) {
                return retried;
            }
            if (retryDecision == OrchestrationModelPort.ReviewDecision.FALLBACK) {
                return executeFallback(step, retried, events, observer);
            }
            AgentTaskResult failed = stepFailure(retried);
            events.emit("STEP_FAILED", step, failed, Map.of(
                    "errorCode", failed.errorCode(),
                    "reasonCode", "RETRY_EXHAUSTED"
            ));
            return failed;
        }
        if (decision == OrchestrationModelPort.ReviewDecision.FALLBACK) {
            return executeFallback(step, initial, events, observer);
        }
        AgentTaskResult failed = stepFailure(initial);
        events.emit("STEP_FAILED", step, failed, Map.of(
                "errorCode", failed.errorCode(),
                "reasonCode", "RECOVERY_EXHAUSTED"
        ));
        return failed;
    }

    private AgentTaskResult executeFallback(
            OrchestrationStep step,
            AgentTaskResult previousResult,
            OrchestrationEventSink events,
            com.jd.genie.platform.agentbridge.ConversationStreamObserver observer
    ) {
        if (directFallbackExecutor == null) {
            // Production wiring has no main-seam fallback. Rather than hard-failing a step whose
            // agent already produced usable output, keep the output and mark the run degraded.
            if (previousResult.status() == AgentTaskResult.Status.SUCCESS) {
                events.emit("STEP_DEGRADED", step, previousResult, Map.of("reasonCode", "REVIEW_UNCERTAIN"));
                return previousResult;
            }
            AgentTaskResult failure = stepFailure(previousResult);
            events.emit("STEP_FAILED", step, failure, Map.of(
                    "errorCode", failure.errorCode(),
                    "reasonCode", "FALLBACK_UNAVAILABLE"
            ));
            return failure;
        }
        events.emit("STEP_FALLBACK_STARTED", step, previousResult, Map.of("reasonCode", "RETRY_EXHAUSTED"));
        com.jd.genie.platform.agentbridge.CancellableAgentCall cancellableCall =
                new com.jd.genie.platform.agentbridge.CancellableAgentCall();
        AgentTaskResult fallbackResult = directFallbackExecutor.executeFallback(
                step.objective(),
                observer,
                cancellableCall
        );
        if (fallbackResult.status() == AgentTaskResult.Status.SUCCESS) {
            events.emit("STEP_DEGRADED", step, fallbackResult, Map.of("reasonCode", "FALLBACK_SUCCEEDED"));
            return fallbackResult;
        }
        events.emit("STEP_FAILED", step, fallbackResult, Map.of(
                "errorCode", fallbackResult.errorCode() == null ? "EXECUTION_ERROR" : fallbackResult.errorCode(),
                "reasonCode", "FALLBACK_FAILED"
        ));
        return AgentTaskResult.failure(
                fallbackResult.errorCode() == null ? "EXECUTION_ERROR" : fallbackResult.errorCode(),
                false
        );
    }

    private AgentTaskResult stepFailure(AgentTaskResult result) {
        return AgentTaskResult.failure(
                result.errorCode() == null || result.errorCode().isBlank() ? "EXECUTION_ERROR" : result.errorCode(),
                false
        );
    }

    private boolean retryNoEligible(int retryNo) {
        return retryNo == 0;
    }

    private OrchestrationModelPort.ReviewDecision review(
            OrchestrationStep step,
            AgentTaskResult result,
            int retryNo,
            OrchestrationEventSink events
    ) {
        events.emit("STEP_REVIEW_STARTED", step, result, Map.of("retryNo", retryNo));
        if (modelPort == null) {
            return result.status() == AgentTaskResult.Status.SUCCESS
                    ? OrchestrationModelPort.ReviewDecision.COMPLETE
                    : result.retryable() && retryNo == 0
                    ? OrchestrationModelPort.ReviewDecision.RETRY
                    : OrchestrationModelPort.ReviewDecision.FALLBACK;
        }
        return modelPort.review(
                step.objective(),
                result.status() == AgentTaskResult.Status.SUCCESS ? result.output() : "",
                result.status() == AgentTaskResult.Status.FAILURE ? result.errorCode() : "",
                result.retryable(),
                retryNo
        );
    }

    private AgentTaskResult executeParallelStep(
            CurrentUser user,
            String query,
            String conversationHistory,
            String longTermMemory,
            OrchestrationStep step,
            Map<String, String> inputs,
            OrchestrationEventSink events,
            AtomicReference<String> runningStepId,
            BooleanSupplier cancellationRequested,
            OrchestrationTraceChannel traceChannel,
            int attemptNo,
            com.jd.genie.platform.agentbridge.ConversationStreamObserver observer,
            List<File> deliverableFiles,
            String fileScope
    ) {
        if (!runningStepId.compareAndSet(null, step.stepId())) {
            throw new AgentBridgeException(MvpErrorCode.INTERNAL_ERROR, "More than one orchestration step is running");
        }
        ExecutorService workers = ParallelStepExecutor.newWorkerPool(step.subTasks().size());
        try {
            events.emit("STEP_STARTED", step, null, Map.of("stepMode", "PARALLEL_AGENTS"));
            events.emit("PARALLEL_STARTED", step, null, Map.of(
                    "stepMode", "PARALLEL_AGENTS",
                    "subTaskCount", step.subTasks().size()
            ));
            Map<OrchestrationSubTask, AgentTaskResult> results = executeParallelSubTasks(
                    user, query, conversationHistory, longTermMemory, step, step.subTasks(), inputs, cancellationRequested, events, workers, traceChannel, attemptNo, observer, deliverableFiles, fileScope
            );
            AgentTaskResult aggregate = ParallelStepExecutor.aggregate(List.copyOf(results.values()));
            if (modelPort == null) {
                events.emit(aggregate.status() == AgentTaskResult.Status.SUCCESS ? "STEP_COMPLETED" : "STEP_FAILED", step,
                        aggregate, aggregate.status() == AgentTaskResult.Status.SUCCESS ? Map.of() : Map.of("errorCode", aggregate.errorCode()));
                return aggregate;
            }
            OrchestrationModelPort.ReviewDecision decision = review(step, aggregate, 0, events);
            if (decision == OrchestrationModelPort.ReviewDecision.RETRY) {
                List<OrchestrationSubTask> retryTargets = results.entrySet().stream()
                        .filter(entry -> entry.getValue().status() == AgentTaskResult.Status.FAILURE && entry.getValue().retryable())
                        .map(Map.Entry::getKey)
                        .toList();
                if (!retryTargets.isEmpty()) {
                    events.emit("STEP_RETRY_STARTED", step, aggregate, Map.of("retryNo", 1));
                    results.putAll(executeParallelSubTasks(
                            user, query, conversationHistory, longTermMemory, step, retryTargets, inputs, cancellationRequested, events, workers, traceChannel, attemptNo, observer, deliverableFiles, fileScope
                    ));
                    aggregate = ParallelStepExecutor.aggregate(List.copyOf(results.values()));
                    decision = review(step, aggregate, 1, events);
                }
            }
            if (decision == OrchestrationModelPort.ReviewDecision.FALLBACK) {
                // executeFallback already emits STEP_FALLBACK_STARTED and the
                // single STEP_DEGRADED / STEP_FAILED terminal for the step.
                return executeFallback(step, aggregate, events, observer);
            }
            if (decision != OrchestrationModelPort.ReviewDecision.COMPLETE
                    || aggregate.status() == AgentTaskResult.Status.FAILURE) {
                aggregate = AgentTaskResult.failure(aggregate.errorCode() == null ? "EXECUTION_ERROR" : aggregate.errorCode(), false);
            }
            events.emit(aggregate.status() == AgentTaskResult.Status.SUCCESS ? "STEP_COMPLETED" : "STEP_FAILED", step,
                    aggregate, aggregate.status() == AgentTaskResult.Status.SUCCESS ? Map.of() : Map.of("errorCode", aggregate.errorCode()));
            return aggregate;
        } finally {
            ParallelStepExecutor.shutdown(workers, step.stepId());
            runningStepId.set(null);
        }
    }

    private Map<OrchestrationSubTask, AgentTaskResult> executeParallelSubTasks(
            CurrentUser user, String query, String conversationHistory, String longTermMemory, OrchestrationStep step, List<OrchestrationSubTask> subTasks,
            Map<String, String> inputs, BooleanSupplier cancellationRequested, OrchestrationEventSink events,
            ExecutorService workers,
            OrchestrationTraceChannel traceChannel, int attemptNo,
            com.jd.genie.platform.agentbridge.ConversationStreamObserver observer,
            List<File> deliverableFiles,
            String fileScope
    ) {
        return ParallelStepExecutor.runAll(subTasks, workers, cancellationRequested, subTask -> executeSubTask(
                user, query, conversationHistory, longTermMemory, step, subTask, inputs,
                cancellationRequested, events, traceChannel, attemptNo, observer, deliverableFiles, fileScope));
    }

    private AgentTaskResult executeSubTask(
            CurrentUser user,
            String query,
            String conversationHistory,
            String longTermMemory,
            OrchestrationStep step,
            OrchestrationSubTask subTask,
            Map<String, String> inputs,
            BooleanSupplier cancellationRequested,
            OrchestrationEventSink events,
            OrchestrationTraceChannel traceChannel,
            int attemptNo,
            com.jd.genie.platform.agentbridge.ConversationStreamObserver observer,
            List<File> deliverableFiles,
            String fileScope
    ) {
        AtomicReference<String> subTaskRunningStep = new AtomicReference<>();
        requestRunningStep.set(subTaskRunningStep);
        OrchestrationEventSink subTaskEvents = (eventType, parentStep, result, details) -> {
            Map<String, Object> subDetails = new LinkedHashMap<>(details);
            subDetails.put("subTaskId", subTask.subTaskId());
            events.emit(ParallelStepExecutor.subTaskEventType(eventType), parentStep, result, Map.copyOf(subDetails));
        };
        try {
            if (cancellationRequested.getAsBoolean()) {
                throw new AgentBridgeException(MvpErrorCode.CLIENT_DISCONNECTED, "Orchestration cancelled before subTask launch");
            }
            return executeStep(
                    user,
                    query,
                    conversationHistory,
                    longTermMemory,
                    step,
                    subTask.subTaskId(),
                    subTask.agentId(),
                    ParallelStepExecutor.combinedSubTaskObjective(step.objective(), subTask.objective()),
                    inputs,
                    new LinkedHashMap<>(),
                    subTaskEvents,
                    subTaskRunningStep,
                    cancellationRequested,
                    traceChannel,
                    attemptNo,
                    true,
                    observer,
                    deliverableFiles,
                    fileScope,
                    false
            );
        } finally {
            requestRunningStep.remove();
        }
    }

    private AgentTaskResult executeStep(
            CurrentUser user,
            String query,
            String conversationHistory,
            String longTermMemory,
            OrchestrationStep step,
            String executionId,
            String agentId,
            String objective,
            Map<String, String> inputs,
            Map<String, AgentTaskResult> reusableResults,
            OrchestrationEventSink events,
            AtomicReference<String> runningStepId,
            BooleanSupplier cancellationRequested,
            OrchestrationTraceChannel traceChannel,
            int attemptNo,
            boolean subTask,
            com.jd.genie.platform.agentbridge.ConversationStreamObserver observer,
            List<File> deliverableFiles,
            String fileScope,
            boolean finalAnswer
    ) {
        if (!runningStepId.compareAndSet(null, step.stepId())) {
            throw new AgentBridgeException(MvpErrorCode.INTERNAL_ERROR, "More than one orchestration step is running");
        }
        ConfiguredAgentPrinter printer = new ConfiguredAgentPrinter();
        try {
            if (SystemResourceBuilder.isSystemAgent(agentId)) {
                return executeSystemResourceStep(user, query, step, executionId, events, traceChannel, attemptNo, subTask);
            }
            AgentRuntimeProfile profile = catalogPort.loadOnlineProfile(user, agentId);
            String agentName = profile.name() == null || profile.name().isBlank() ? agentId : profile.name();
            printer = new ConfiguredAgentPrinter(
                    traceChannel, observer, attemptNo, step.stepId(), agentId, agentName,
                    subTask ? executionId : null);
            String signature = resultSignature(agentId, objective, inputs, profile.agentVersion(), longTermMemory);
            AgentTaskResult reused = reusableResults.get(signature);
            if (reused != null) {
                emitExecutionEvent(events, "COMPLETED", step, executionId, agentId, agentName, reused, Map.of(
                        "reasonCode", "REUSED",
                        "agentName", agentName
                ), subTask);
                if (traceChannel != null && reused.status() == AgentTaskResult.Status.SUCCESS) {
                    emitExecutionTrace(traceChannel, attemptNo, step.stepId(), executionId, agentId, agentName,
                            OrchestrationTraceChannel.KIND_OUTPUT,
                            reused.output() == null ? "" : reused.output(),
                            false,
                            subTask);
                }
                return reused;
            }
            List<File> productFiles = priorDeliverables(deliverableFiles);
            List<File> taskProductFiles = new ArrayList<>();
            String normalizedObjective = objective == null ? "" : objective.trim();
            emitExecutionEvent(events, "STARTED", step, executionId, agentId, agentName, null, Map.of(
                    "agentId", agentId,
                    "agentName", agentName
            ), subTask);
            if (traceChannel != null) {
                emitExecutionTrace(traceChannel, attemptNo, step.stepId(), executionId, agentId, agentName,
                        OrchestrationTraceChannel.KIND_STATUS,
                        "开始执行：" + normalizedObjective,
                        false,
                        subTask);
            }
            // Keep the original user question as a topic bound only; the executable task is still the step objective.
            String stepQuery = StepQueryBuilder.build(
                    agentName,
                    profile.description(),
                    query,
                    conversationHistory,
                    normalizedObjective,
                    longTermMemory,
                    inputs,
                    finalAnswer
            );
            AgentContext context = AgentContext.builder()
                    .requestId(executionId)
                    .sessionId(fileScope)
                    .query(stepQuery)
                    .task(normalizedObjective)
                    .basePrompt(stepQuery)
                    .dateInfo(DateUtil.CurrentDateInfo())
                    .productFiles(productFiles)
                    .taskProductFiles(taskProductFiles)
                    .isStream(true)
                    .templateType("empty")
                    .build();
            List<BaseTool> runtimeTools = new ArrayList<>();
            if (skillRuntimePort != null) {
                runtimeTools.addAll(skillRuntimePort.buildRuntimeTools(user, profile, context));
            }
            // Only register the browser workspace tool when this request actually carries a
            // bound workspace snapshot — otherwise every ordinary chat would pay for an unused
            // tool schema and its system-prompt instructions on every single turn.
            if (browserWorkspacePythonToolFactory != null
                    && BrowserWorkspaceContextPolicy.hasSnapshot(context.getQuery())) {
                runtimeTools.add(browserWorkspacePythonToolFactory.create(user, context));
            }
            ToolCollection tools = toolCollectionPort.build(user, profile, context, runtimeTools);
            context.setToolCollection(tools);
            if (cancellationRequested.getAsBoolean()) {
                throw new AgentBridgeException(MvpErrorCode.CLIENT_DISCONNECTED, "Orchestration cancelled before Agent launch");
            }
            AgentTaskResult result = executor.execute(context, profile, printer, maxAgentSteps);
            DeliverableFiles.collect(deliverableFiles, context.getProductFiles());
            if (result.status() == AgentTaskResult.Status.SUCCESS) {
                reusableResults.put(signature, result);
            }
            emitExecutionEvent(
                    events,
                    result.status() == AgentTaskResult.Status.SUCCESS ? "COMPLETED" : "FAILED",
                    step,
                    executionId,
                    agentId,
                    agentName,
                    result,
                    Map.of("agentName", agentName),
                    subTask
            );
            if (traceChannel != null) {
                if (result.status() == AgentTaskResult.Status.SUCCESS) {
                    emitExecutionTrace(traceChannel, attemptNo, step.stepId(), executionId, agentId, agentName,
                            OrchestrationTraceChannel.KIND_OUTPUT,
                            result.output() == null ? "" : result.output(),
                            false,
                            subTask);
                } else {
                    emitExecutionTrace(traceChannel, attemptNo, step.stepId(), executionId, agentId, agentName,
                            OrchestrationTraceChannel.KIND_ERROR,
                            result.errorCode() == null ? "EXECUTION_ERROR" : result.errorCode(),
                            false,
                            subTask);
                }
            }
            return result;
        } catch (AgentBridgeException error) {
            if (error.getErrorCode() == MvpErrorCode.CLIENT_DISCONNECTED) {
                throw error;
            }
            String recovered = printer.recoveredOutput();
            if (error.getErrorCode() == MvpErrorCode.AGENT_INVALID_RESULT
                    && recovered != null && !recovered.isBlank()) {
                log.warn("Recovered markdown agent output after invalid envelope agentId={} stepId={} executionId={}",
                        agentId, step.stepId(), executionId);
                AgentTaskResult recoveredResult = AgentTaskResult.success(recovered);
                String recoveredName = agentNameOf(agentId);
                emitExecutionEvent(
                        events, "COMPLETED", step, executionId, agentId, recoveredName, recoveredResult,
                        Map.of("agentName", recoveredName, "reasonCode", "RECOVERED_OUTPUT"), subTask);
                if (traceChannel != null) {
                    emitExecutionTrace(traceChannel, attemptNo, step.stepId(), executionId, agentId, recoveredName,
                            OrchestrationTraceChannel.KIND_OUTPUT, recovered, false, subTask);
                }
                return recoveredResult;
            }
            log.warn("Orchestration execution failed agentId={} stepId={} executionId={} code={}",
                    agentId, step.stepId(), executionId, error.getErrorCode(), error);
            String errorCode = orchestrationErrorCode(error.getErrorCode());
            AgentTaskResult failure = AgentTaskResult.failure(errorCode, errorCode.equals("TOOL_TIMEOUT") || errorCode.equals("TOOL_UNAVAILABLE") || errorCode.equals("EXECUTION_ERROR"));
            emitExecutionEvent(events, "FAILED", step, executionId, agentId, agentNameOf(agentId), failure,
                    Map.of("errorCode", errorCode), subTask);
            if (traceChannel != null) {
                emitExecutionTrace(traceChannel, attemptNo, step.stepId(), executionId, agentId, agentNameOf(agentId),
                        OrchestrationTraceChannel.KIND_ERROR, errorCode, false, subTask);
            }
            return failure;
        } catch (Exception error) {
            log.error("Orchestration execution crashed agentId={} stepId={} executionId={}", agentId, step.stepId(), executionId, error);
            AgentTaskResult failure = AgentTaskResult.failure("EXECUTION_ERROR", true);
            emitExecutionEvent(events, "FAILED", step, executionId, agentId, agentNameOf(agentId), failure,
                    Map.of("errorCode", "EXECUTION_ERROR"), subTask);
            if (traceChannel != null) {
                emitExecutionTrace(traceChannel, attemptNo, step.stepId(), executionId, agentId, agentNameOf(agentId),
                        OrchestrationTraceChannel.KIND_ERROR, "EXECUTION_ERROR", false, subTask);
            }
            return failure;
        } finally {
            printer.close();
            runningStepId.set(null);
        }
    }

    private AgentTaskResult executeSystemResourceStep(
            CurrentUser user,
            String query,
            OrchestrationStep step,
            String executionId,
            OrchestrationEventSink events,
            OrchestrationTraceChannel traceChannel,
            int attemptNo,
            boolean subTask
    ) {
        String agentName = SystemResourceBuilder.candidate().name();
        if (systemResourceBuilder == null) {
            return AgentTaskResult.failure("SYSTEM_RESOURCE_BUILDER_UNAVAILABLE", false);
        }
        emitExecutionEvent(events, "STARTED", step, executionId, SystemResourceBuilder.AGENT_ID, agentName, null, Map.of("agentName", agentName), subTask);
        if (traceChannel != null) {
            emitExecutionTrace(traceChannel, attemptNo, step.stepId(), executionId, SystemResourceBuilder.AGENT_ID, agentName,
                    OrchestrationTraceChannel.KIND_STATUS, "开始执行：" + step.objective(), false, subTask);
        }
        AgentTaskResult result = AgentTaskResult.success(systemResourceBuilder.create(user, query, step.objective()));
        emitExecutionEvent(events, "COMPLETED", step, executionId, SystemResourceBuilder.AGENT_ID, agentName, result, Map.of("agentName", agentName), subTask);
        if (traceChannel != null) {
            emitExecutionTrace(traceChannel, attemptNo, step.stepId(), executionId, SystemResourceBuilder.AGENT_ID, agentName,
                    OrchestrationTraceChannel.KIND_OUTPUT, result.output(), false, subTask);
        }
        return result;
    }

    private void emitExecutionEvent(
            OrchestrationEventSink events,
            String phase,
            OrchestrationStep step,
            String executionId,
            String agentId,
            String agentName,
            AgentTaskResult result,
            Map<String, Object> details,
            boolean subTask
    ) {
        Map<String, Object> eventDetails = new LinkedHashMap<>(details);
        eventDetails.put("agentId", agentId);
        eventDetails.put("agentName", agentName);
        events.emit("STEP_" + phase, step, result, Map.copyOf(eventDetails));
    }

    private void emitExecutionTrace(
            OrchestrationTraceChannel traceChannel,
            int attemptNo,
            String stepId,
            String executionId,
            String agentId,
            String agentName,
            String kind,
            String text,
            boolean append,
            boolean subTask
    ) {
        if (subTask) {
            traceChannel.emitSubTask(attemptNo, null, stepId, executionId, agentId, agentName, kind, text, append);
        } else {
            traceChannel.emitStep(attemptNo, stepId, agentId, agentName, kind, text, append);
        }
    }

    private String agentNameOf(String agentId) {
        return agentId == null ? "" : agentId;
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

    private static String resolveFileScope(String fileSessionId) {
        if (fileSessionId == null || fileSessionId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return fileSessionId.trim();
    }

    private static List<File> priorDeliverables(List<File> deliverableFiles) {
        if (deliverableFiles == null || deliverableFiles.isEmpty()) {
            return new ArrayList<>();
        }
        synchronized (deliverableFiles) {
            return new ArrayList<>(deliverableFiles);
        }
    }

    private String resultSignature(
            String agentId,
            String objective,
            Map<String, String> inputs,
            long agentVersion,
            String longTermMemory
    ) {
        String source = (objective == null ? "" : objective).trim().replaceAll("\\s+", " ")
                + "\u0000" + agentVersion
                + "\u0000" + sha256(longTermMemory == null ? "" : longTermMemory)
                + "\u0000" + agentId
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
