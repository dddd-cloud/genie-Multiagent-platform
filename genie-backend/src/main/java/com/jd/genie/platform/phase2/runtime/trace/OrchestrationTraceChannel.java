package com.jd.genie.platform.phase2.runtime.trace;

import com.jd.genie.model.response.GptProcessResult;
import com.jd.genie.platform.agentbridge.ConversationStreamObserver;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Parallel live-trace channel for orchestration UI.
 * Does not modify frozen orchestrationEvent V1 payloads.
 */
public final class OrchestrationTraceChannel {
    public static final String PACKAGE_TYPE = "orchestration_trace";
    public static final int MAX_CHUNK_CHARS = 4_096;
    public static final int MAX_STEP_CHARS = 24_576;

    public static final String SCOPE_MAIN = "MAIN";
    public static final String SCOPE_STEP = "STEP";
    public static final String SCOPE_SUBTASK = "SUBTASK";
    public static final String KIND_STATUS = "STATUS";
    public static final String KIND_THOUGHT = "THOUGHT";
    public static final String KIND_OUTPUT = "OUTPUT";
    public static final String KIND_ERROR = "ERROR";

    private static final String DEFAULT_MAIN_NAME = "主 Agent";

    private final ConversationStreamObserver observer;
    private final String requestId;
    private final String runId;
    private final AtomicLong sequence;
    private final String mainDisplayName;
    // PARALLEL_AGENTS subtasks share one channel across worker threads.
    private final Map<String, Integer> stepEmittedChars = new ConcurrentHashMap<>();
    private final Object emitLock = new Object();

    public OrchestrationTraceChannel(
            ConversationStreamObserver observer,
            String requestId,
            String runId,
            AtomicLong sequence
    ) {
        this(observer, requestId, runId, sequence, DEFAULT_MAIN_NAME);
    }

    public OrchestrationTraceChannel(
            ConversationStreamObserver observer,
            String requestId,
            String runId,
            AtomicLong sequence,
            String mainDisplayName
    ) {
        this.observer = Objects.requireNonNull(observer, "observer");
        this.requestId = Objects.requireNonNull(requestId, "requestId");
        this.runId = Objects.requireNonNull(runId, "runId");
        this.sequence = Objects.requireNonNull(sequence, "sequence");
        this.mainDisplayName = mainDisplayName == null || mainDisplayName.isBlank()
                ? DEFAULT_MAIN_NAME
                : mainDisplayName;
    }

    public void emitMain(String kind, String text, boolean append) {
        emit(SCOPE_MAIN, null, null, null, null, null, mainDisplayName, kind, text, append);
    }

    public void emitMain(Integer attemptNo, String kind, String text, boolean append) {
        emit(SCOPE_MAIN, attemptNo, null, null, null, null, mainDisplayName, kind, text, append);
    }

    public void emitStep(
            Integer attemptNo,
            String stepId,
            String agentId,
            String agentName,
            String kind,
            String text,
            boolean append
    ) {
        emit(SCOPE_STEP, attemptNo, null, stepId, null, agentId, agentName, kind, text, append);
    }

    public void emitSubTask(
            Integer attemptNo,
            Integer retryNo,
            String stepId,
            String subTaskId,
            String agentId,
            String agentName,
            String kind,
            String text,
            boolean append
    ) {
        emit(SCOPE_SUBTASK, attemptNo, retryNo, stepId, subTaskId, agentId, agentName, kind, text, append);
    }

    private void emit(
            String scope,
            Integer attemptNo,
            Integer retryNo,
            String stepId,
            String subTaskId,
            String agentId,
            String agentName,
            String kind,
            String text,
            boolean append
    ) {
        synchronized (emitLock) {
            emitLocked(scope, attemptNo, retryNo, stepId, subTaskId, agentId, agentName, kind, text, append);
        }
    }

    private void emitLocked(
            String scope,
            Integer attemptNo,
            Integer retryNo,
            String stepId,
            String subTaskId,
            String agentId,
            String agentName,
            String kind,
            String text,
            boolean append
    ) {
        if (observer.isTerminal()) {
            return;
        }
        String safeKind = kind == null ? KIND_STATUS : kind;
        String raw = text == null ? "" : text;
        String budgetKey = scope + ":" + (stepId == null ? "main" : stepId) +
                          ":" + (subTaskId == null ? "" : subTaskId) + ":" + safeKind;
        int emitted = stepEmittedChars.getOrDefault(budgetKey, 0);
        if (emitted >= MAX_STEP_CHARS) {
            return;
        }
        boolean truncated = false;
        String chunk = raw;
        int remaining = MAX_STEP_CHARS - emitted;
        if (chunk.length() > MAX_CHUNK_CHARS) {
            chunk = chunk.substring(0, MAX_CHUNK_CHARS);
            truncated = true;
        }
        if (chunk.length() > remaining) {
            chunk = chunk.substring(0, Math.max(0, remaining));
            truncated = true;
        }
        if (chunk.isEmpty() && !KIND_STATUS.equals(safeKind) && !KIND_ERROR.equals(safeKind)) {
            return;
        }
        stepEmittedChars.put(budgetKey, emitted + chunk.length());

        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("schemaVersion", 2);
        trace.put("sequence", sequence.incrementAndGet());
        trace.put("requestId", requestId);
        trace.put("runId", runId);
        trace.put("scope", scope);
        trace.put("attemptNo", attemptNo);
        trace.put("retryNo", retryNo);
        trace.put("stepId", stepId);
        trace.put("subTaskId", subTaskId);
        trace.put("agentId", agentId);
        trace.put("agentName", agentName == null || agentName.isBlank() ? agentId : agentName);
        trace.put("kind", safeKind);
        trace.put("text", chunk);
        trace.put("append", append);
        trace.put("truncated", truncated);

        GptProcessResult packet = GptProcessResult.builder()
                .status("running")
                .response("")
                .responseAll("")
                .finished(false)
                .packageType(PACKAGE_TYPE)
                .resultMap(Map.of("orchestrationTrace", trace))
                .build();
        // Thoughts are progress-only; a transient write failure must not abort the run.
        if (KIND_THOUGHT.equals(safeKind)) {
            observer.onEventBestEffort(packet);
        } else {
            observer.onEvent(packet);
        }
    }
}
