package com.jd.genie.platform.phase2contract.dto;

import java.util.List;

public record AgentRuntimeProfile(
    String agentId,
    long agentVersion,
    String name,
    String description,
    String compiledSystemPromptTemplate,
    String resolvedModelName,
    List<AgentRuntimeSkill> skills,
    List<String> capabilityKeys
) {
    public AgentRuntimeProfile {
        skills = skills == null ? List.of() : List.copyOf(skills);
        capabilityKeys = capabilityKeys == null
            ? List.of()
            : List.copyOf(capabilityKeys);
    }
}
