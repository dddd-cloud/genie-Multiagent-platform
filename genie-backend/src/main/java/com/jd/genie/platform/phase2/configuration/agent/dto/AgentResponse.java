package com.jd.genie.platform.phase2.configuration.agent.dto;

import java.time.Instant;
import java.util.List;

public record AgentResponse(
    String id,
    String name,
    String description,
    String promptMode,
    String promptConfig,
    String systemPrompt,
    String modelName,
    String status,
    Long version,
    List<String> skillIds,
    List<String> capabilityKeys,
    Instant createdAt,
    Instant updatedAt
) {
    public AgentResponse {
        skillIds = skillIds == null ? List.of() : List.copyOf(skillIds);
        capabilityKeys = capabilityKeys == null ? List.of() : List.copyOf(capabilityKeys);
    }
}