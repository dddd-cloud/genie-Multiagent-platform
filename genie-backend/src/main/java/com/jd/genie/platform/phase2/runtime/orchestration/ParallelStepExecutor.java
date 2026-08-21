package com.jd.genie.platform.phase2.runtime.orchestration;

import com.jd.genie.agent.llm.LLMSettings;
import com.jd.genie.agent.llm.RequestScopedLlmSettings;
import com.jd.genie.agent.llm.RequestTokenUsage;
import com.jd.genie.platform.agentbridge.AgentBridgeException;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2.runtime.agent.AgentTaskResult;
import com.jd.genie.platform.phase2.runtime.plan.OrchestrationSubTask;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/**
 * Worker pool plumbing and result aggregation for PARALLEL_AGENTS steps.
 * Holds no orchestration policy: the caller supplies the per-subtask work.
 */
@Slf4j
final class ParallelStepExecutor {
    private static final int MAX_PARALLEL_WORKERS = 4;
    private static final long CANCELLATION_POLL_MILLIS = 50;
    private static final long SHUTDOWN_WAIT_SECONDS = 1;

    private ParallelStepExecutor() {
    }

    static ExecutorService newWorkerPool(int subTaskCount) {
        return Executors.newFixedThreadPool(Math.min(MAX_PARALLEL_WORKERS, Math.max(1, subTaskCount)));
    }

    static void shutdown(ExecutorService workers, String stepId) {
        workers.shutdownNow();
        try {
            if (!workers.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS)) {
                log.warn("Parallel orchestration workers did not terminate stepId={}", stepId);
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
    }

    /** Runs every subtask on the pool and returns results in submission order. */
    static Map<OrchestrationSubTask, AgentTaskResult> runAll(
            List<OrchestrationSubTask> subTasks,
            ExecutorService workers,
            BooleanSupplier cancellationRequested,
            Function<OrchestrationSubTask, AgentTaskResult> work
    ) {
        LLMSettings llmSettings = RequestScopedLlmSettings.get();
        String billingRequestId = RequestTokenUsage.getBillingRequestId();
        List<CompletableFuture<AgentTaskResult>> futures = subTasks.stream()
                .map(subTask -> CompletableFuture.supplyAsync(() -> {
                    RequestScopedLlmSettings.set(llmSettings);
                    RequestTokenUsage.setBillingRequestId(billingRequestId);
                    try {
                        return work.apply(subTask);
                    } finally {
                        RequestScopedLlmSettings.clear();
                        RequestTokenUsage.clearBillingRequestId();
                    }
                }, workers))
                .toList();
        awaitCompletion(futures, cancellationRequested);
        Map<OrchestrationSubTask, AgentTaskResult> results = new LinkedHashMap<>();
        for (int index = 0; index < subTasks.size(); index++) {
            results.put(subTasks.get(index), futures.get(index).join());
        }
        return results;
    }

    /** SUCCESS with joined outputs, or the first failure encountered. */
    static AgentTaskResult aggregate(List<AgentTaskResult> results) {
        List<String> outputs = new ArrayList<>();
        AgentTaskResult failure = null;
        for (AgentTaskResult result : results) {
            if (result.status() == AgentTaskResult.Status.SUCCESS) {
                outputs.add(result.output());
            } else if (failure == null) {
                failure = result;
            }
        }
        if (failure != null) {
            return AgentTaskResult.failure(failure.errorCode(), failure.retryable());
        }
        return AgentTaskResult.success(String.join("\n\n", outputs));
    }

    static String subTaskEventType(String executionEventType) {
        return switch (executionEventType) {
            case "STEP_STARTED" -> "SUBTASK_STARTED";
            case "STEP_COMPLETED" -> "SUBTASK_COMPLETED";
            case "STEP_FAILED" -> "SUBTASK_FAILED";
            default -> executionEventType;
        };
    }

    static String combinedSubTaskObjective(String stepObjective, String subTaskObjective) {
        return "所属顶层步骤目标：\n" + (stepObjective == null ? "" : stepObjective.trim())
                + "\n\n当前子任务目标：\n" + (subTaskObjective == null ? "" : subTaskObjective.trim());
    }

    private static void awaitCompletion(
            List<CompletableFuture<AgentTaskResult>> futures,
            BooleanSupplier cancellationRequested
    ) {
        CompletableFuture<Void> all = CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
        try {
            while (true) {
                if (cancellationRequested.getAsBoolean()) {
                    cancelOutstanding(futures);
                    throw new AgentBridgeException(MvpErrorCode.CLIENT_DISCONNECTED, "Orchestration cancelled during parallel step");
                }
                try {
                    all.get(CANCELLATION_POLL_MILLIS, TimeUnit.MILLISECONDS);
                    return;
                } catch (TimeoutException ignored) {
                    // Poll cancellation without converting the top-level sequence into a DAG.
                }
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            cancelOutstanding(futures);
            throw new AgentBridgeException(MvpErrorCode.CLIENT_DISCONNECTED, "Orchestration parallel step interrupted", error);
        } catch (ExecutionException error) {
            cancelOutstanding(futures);
            Throwable cause = error.getCause();
            if (cause instanceof AgentBridgeException bridgeException
                    && bridgeException.getErrorCode() == MvpErrorCode.CLIENT_DISCONNECTED) {
                throw bridgeException;
            }
            throw new AgentBridgeException(MvpErrorCode.INTERNAL_ERROR, "Orchestration parallel step failed", cause);
        }
    }

    private static void cancelOutstanding(List<CompletableFuture<AgentTaskResult>> futures) {
        futures.stream().filter(future -> !future.isDone()).forEach(future -> future.cancel(true));
    }
}
