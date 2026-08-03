package com.jd.genie.platform.phase2contract.dto;

public record AgentRuntimeSkill(
    String skillId,
    long skillVersion,
    int sortOrder,
    String instruction,
    String outputRequirement
) {
}
