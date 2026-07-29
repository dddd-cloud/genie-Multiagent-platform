package com.jd.genie.platform.agentbridge.acceptance;

import java.util.Locale;

public enum FakeAgentMode {
    SUCCESS,
    HTTP_500,
    DISCONNECT_AFTER_N_EVENTS,
    MALFORMED_EVENT,
    NO_FINAL_EVENT,
    SLOW_STREAM,
    SNAPSHOT_TOO_LARGE;

    public static FakeAgentMode fromConfiguration(String configuredValue) {
        if (configuredValue == null || configuredValue.isBlank()) {
            throw new IllegalArgumentException("MVP_FAKE_AGENT_MODE must not be blank");
        }
        try {
            return valueOf(configuredValue.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException(
                    "Unsupported MVP_FAKE_AGENT_MODE: " + configuredValue,
                    error
            );
        }
    }
}
