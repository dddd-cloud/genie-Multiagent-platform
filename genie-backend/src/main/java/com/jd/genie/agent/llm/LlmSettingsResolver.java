package com.jd.genie.agent.llm;

import com.jd.genie.config.GenieConfig;

import java.util.Map;

/**
 * Direct agent execution already falls back across llm.settings entries.
 * Orchestration/memory clients must do the same, otherwise Ensemble fails
 * when the planner key does not match the populated map entry.
 */
public final class LlmSettingsResolver {
    private LlmSettingsResolver() {
    }

    public static LLMSettings resolveComplete(GenieConfig config) {
        if (config == null) {
            return null;
        }
        Map<String, LLMSettings> settingsMap = config.getLlmSettingsMap();
        LLMSettings named = firstComplete(
            lookup(settingsMap, config.getPlannerModelName()),
            lookup(settingsMap, config.getReactModelName()),
            lookup(settingsMap, config.getExecutorModelName())
        );
        if (named != null) {
            return named;
        }
        if (settingsMap == null) {
            return null;
        }
        for (LLMSettings settings : settingsMap.values()) {
            if (isComplete(settings)) {
                return settings;
            }
        }
        return null;
    }

    private static LLMSettings firstComplete(LLMSettings... candidates) {
        if (candidates == null) {
            return null;
        }
        for (LLMSettings settings : candidates) {
            if (isComplete(settings)) {
                return settings;
            }
        }
        return null;
    }

    private static LLMSettings lookup(Map<String, LLMSettings> settingsMap, String modelName) {
        if (settingsMap == null || modelName == null || modelName.isBlank()) {
            return null;
        }
        return settingsMap.get(modelName);
    }

    static boolean isComplete(LLMSettings settings) {
        return settings != null
            && hasText(settings.getApiKey())
            && hasText(settings.getBaseUrl());
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
