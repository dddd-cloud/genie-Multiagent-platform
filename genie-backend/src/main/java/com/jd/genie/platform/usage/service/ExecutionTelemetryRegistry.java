package com.jd.genie.platform.usage.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * The execution port only carries {@code assistantMessageId} into its terminal callbacks, while the
 * conversation id, request id and start time are known at prepare time. This registry bridges the two
 * without touching the frozen contract.
 *
 * <p>A stream that never reaches a terminal state would leak an entry, so the registry is bounded and
 * evicts in insertion order. Losing an old entry only degrades a usage row to "duration unknown";
 * it never blocks or fails a conversation.
 */
@Component
public class ExecutionTelemetryRegistry {

    public record Telemetry(String conversationId, String requestId, long startedAtMillis) {
    }

    private final Map<String, Telemetry> entries = new ConcurrentHashMap<>();
    private final Queue<String> insertionOrder = new ConcurrentLinkedQueue<>();
    private final int maxEntries;

    public ExecutionTelemetryRegistry(@Value("${GENIE_USAGE_TELEMETRY_MAX_ENTRIES:10000}") int maxEntries) {
        this.maxEntries = Math.max(1, maxEntries);
    }

    public void register(String assistantMessageId, String conversationId, String requestId, long startedAtMillis) {
        if (assistantMessageId == null) {
            return;
        }
        if (entries.put(assistantMessageId, new Telemetry(conversationId, requestId, startedAtMillis)) == null) {
            insertionOrder.add(assistantMessageId);
        }
        while (entries.size() > maxEntries) {
            String oldest = insertionOrder.poll();
            if (oldest == null) {
                break;
            }
            entries.remove(oldest);
        }
    }

    /** Returns and removes the telemetry for a finished turn, or null when it is no longer known. */
    public Telemetry consume(String assistantMessageId) {
        if (assistantMessageId == null) {
            return null;
        }
        Telemetry telemetry = entries.remove(assistantMessageId);
        if (telemetry != null) {
            insertionOrder.remove(assistantMessageId);
        }
        return telemetry;
    }
}
