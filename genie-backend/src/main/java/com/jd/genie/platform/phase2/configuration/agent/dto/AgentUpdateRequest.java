package com.jd.genie.platform.phase2.configuration.agent.dto;

import java.util.List;

public record AgentUpdateRequest(
    Long version,
    String name,
    String description,
    String promptMode,
    String promptConfig,
    String systemPrompt,
    String modelName,
    List<AgentSkillBindingRequest> skills,
    List<String> capabilityKeys
) {
}