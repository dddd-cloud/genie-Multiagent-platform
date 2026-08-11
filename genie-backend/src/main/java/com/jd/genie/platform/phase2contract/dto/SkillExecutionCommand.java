package com.jd.genie.platform.phase2contract.dto;

public record SkillExecutionCommand(
    String skillId,
    String entrypointName,
    String inputJson,
    Integer timeoutMs
) {
}
