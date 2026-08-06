package com.jd.genie.platform.phase2.runtime.agent;

import java.util.Objects;

public record AgentTaskResult(
        Status status,
        String output,
        String errorCode,
        boolean retryable
) {
    public enum Status { SUCCESS, FAILURE }

    public AgentTaskResult {
        Objects.requireNonNull(status, "status");
        if (status == Status.SUCCESS && (output == null || output.isBlank())) {
            throw new IllegalArgumentException("Successful result requires output");
        }
        if (status == Status.FAILURE && (errorCode == null || errorCode.isBlank())) {
            throw new IllegalArgumentException("Failed result requires errorCode");
        }
    }

    public static AgentTaskResult success(String output) {
        return new AgentTaskResult(Status.SUCCESS, output, null, false);
    }

    public static AgentTaskResult failure(String errorCode, boolean retryable) {
        return new AgentTaskResult(Status.FAILURE, null, errorCode, retryable);
    }
}
