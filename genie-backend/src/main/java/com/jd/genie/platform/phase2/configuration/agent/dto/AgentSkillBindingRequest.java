package com.jd.genie.platform.phase2.configuration.agent.dto;

public record AgentSkillBindingRequest(
    String skillId,
    Integer sortOrder
) {
}