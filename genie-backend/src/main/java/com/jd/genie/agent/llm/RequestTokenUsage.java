package com.jd.genie.agent.llm;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Accumulates token counts for the in-flight conversation turn. LLM callbacks run on OkHttp
 * threads, so counts are stored by billing request id rather than ThreadLocal. The billing id
 * itself is ThreadLocal so the agent worker (and copied parallel workers) can stamp each call.
 */
public final class RequestTokenUsage {
    public record Snapshot(String modelName, long promptTokens, long completionTokens, long totalTokens) {
    }

    private static final int MAX_ENTRIES = 10_000;
    private static final ThreadLocal<String> BILLING_REQUEST_ID = new ThreadLocal<>();
    private static final Map<String, Acc> BY_REQUEST = new ConcurrentHashMap<>();
    private static final Queue<String> INSERTION_ORDER = new ConcurrentLinkedQueue<>();

    private RequestTokenUsage() {
    }

    public static void setBillingRequestId(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            BILLING_REQUEST_ID.remove();
        } else {
            BILLING_REQUEST_ID.set(requestId);
        }
    }

    public static String getBillingRequestId() {
        return BILLING_REQUEST_ID.get();
    }

    public static void clearBillingRequestId() {
        BILLING_REQUEST_ID.remove();
    }

    public static String billingKeyOr(String fallbackRequestId) {
        String billingRequestId = BILLING_REQUEST_ID.get();
        if (billingRequestId != null && !billingRequestId.isBlank()) {
            return billingRequestId;
        }
        return fallbackRequestId;
    }

    /**
     * AgentRequest.requestId is often {@code user+session:conversationRequestId}.
     */
    public static String fromAgentRequestId(String agentRequestId) {
        if (agentRequestId == null || agentRequestId.isBlank()) {
            return null;
        }
        int colon = agentRequestId.lastIndexOf(':');
        if (colon >= 0 && colon < agentRequestId.length() - 1) {
            return agentRequestId.substring(colon + 1);
        }
        return agentRequestId;
    }

    public static void add(String requestId, String modelName, long promptTokens, long completionTokens, long totalTokens) {
        if (requestId == null || requestId.isBlank()) {
            return;
        }
        long prompt = Math.max(0L, promptTokens);
        long completion = Math.max(0L, completionTokens);
        long total = totalTokens > 0 ? totalTokens : prompt + completion;
        if (total <= 0) {
            return;
        }
        BY_REQUEST.compute(requestId, (key, existing) -> {
            if (existing == null) {
                INSERTION_ORDER.add(key);
                return new Acc(modelName, prompt, completion, total);
            }
            existing.promptTokens.addAndGet(prompt);
            existing.completionTokens.addAndGet(completion);
            existing.totalTokens.addAndGet(total);
            if (existing.modelName == null && modelName != null && !modelName.isBlank()) {
                existing.modelName = modelName;
            }
            return existing;
        });
        evictOverflow();
    }

    public static Snapshot consume(String requestId) {
        if (requestId == null) {
            return null;
        }
        Acc acc = BY_REQUEST.remove(requestId);
        if (acc == null) {
            return null;
        }
        INSERTION_ORDER.remove(requestId);
        return acc.snapshot();
    }

    private static void evictOverflow() {
        while (BY_REQUEST.size() > MAX_ENTRIES) {
            String oldest = INSERTION_ORDER.poll();
            if (oldest == null) {
                break;
            }
            BY_REQUEST.remove(oldest);
        }
    }

    private static final class Acc {
        private volatile String modelName;
        private final AtomicLong promptTokens;
        private final AtomicLong completionTokens;
        private final AtomicLong totalTokens;

        private Acc(String modelName, long promptTokens, long completionTokens, long totalTokens) {
            this.modelName = modelName;
            this.promptTokens = new AtomicLong(promptTokens);
            this.completionTokens = new AtomicLong(completionTokens);
            this.totalTokens = new AtomicLong(totalTokens);
        }

        private Snapshot snapshot() {
            return new Snapshot(modelName, promptTokens.get(), completionTokens.get(), totalTokens.get());
        }
    }
}
