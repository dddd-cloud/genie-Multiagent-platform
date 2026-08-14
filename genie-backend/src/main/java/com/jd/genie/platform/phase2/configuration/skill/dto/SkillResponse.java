package com.jd.genie.platform.phase2.configuration.skill.dto;

import com.jd.genie.platform.phase2contract.dto.SkillEntrypointView;

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
    Instant updatedAt,
    String packageMode,
    String packageHash,
    List<SkillEntrypointView> entrypoints
) {
    public SkillResponse {
        capabilityKeys = capabilityKeys == null ? List.of() : List.copyOf(capabilityKeys);
        entrypoints = entrypoints == null ? List.of() : List.copyOf(entrypoints);
        outputRequirement = outputRequirement == null ? "" : outputRequirement;
        description = description == null ? "" : description;
    }

    /** Backward-compatible constructor for existing CRUD tests. */
    public SkillResponse(
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
        this(id, name, description, instruction, outputRequirement, status, version, capabilityKeys,
            createdAt, updatedAt, null, null, List.of());
    }
}