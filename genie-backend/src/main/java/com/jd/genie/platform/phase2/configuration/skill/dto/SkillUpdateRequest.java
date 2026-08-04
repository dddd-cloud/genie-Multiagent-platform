package com.jd.genie.platform.phase2.configuration.skill.dto;

import java.util.List;

public record SkillUpdateRequest(
    Long version,
    String name,
    String description,
    String instruction,
    String outputRequirement,
    List<String> capabilityKeys
) {
}