package com.jd.genie.agent.llm;

/**
 * Holds the LLM settings chosen for the current agent-worker thread.
 * Must be set on the same thread that constructs {@link LLM}, then cleared in a finally block.
 */
public final class RequestScopedLlmSettings {
    private static final ThreadLocal<LLMSettings> HOLDER = new ThreadLocal<>();

    private RequestScopedLlmSettings() {
    }

    public static void set(LLMSettings settings) {
        if (settings == null) {
            HOLDER.remove();
        } else {
            HOLDER.set(settings);
        }
    }

    public static LLMSettings get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
