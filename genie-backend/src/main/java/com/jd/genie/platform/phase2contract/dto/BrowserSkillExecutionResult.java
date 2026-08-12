package com.jd.genie.platform.phase2contract.dto;

public record BrowserSkillExecutionResult(
    int schemaVersion,
    String executionId,
    boolean success,
    String outputJson,
    String stdout,
    String stderr,
    String errorCode,
    String message
) {
}
