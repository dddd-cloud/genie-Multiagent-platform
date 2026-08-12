package com.jd.genie.platform.phase2contract.dto;

public record BrowserSkillExecutionSignal(
    int schemaVersion,
    String executionId,
    String skillId,
    String entrypointName,
    String packageHash,
    long timeoutMs
) {
}
