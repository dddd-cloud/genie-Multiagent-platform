package com.jd.genie.platform.phase2.configuration.skill.dto;

import java.time.Instant;
import java.util.List;

public record SkillResponse(
    String id,
    String name,
    String description,
    String instruction,
    String outputRequirement,
    String status,
    Long version,
    List<String> capabilityKeys,
    Instant createdAt,
    Instant updatedAt
) {
    public SkillResponse {
        capabilityKeys = capabilityKeys == null ? List.of() : List.copyOf(capabilityKeys);
    }
}