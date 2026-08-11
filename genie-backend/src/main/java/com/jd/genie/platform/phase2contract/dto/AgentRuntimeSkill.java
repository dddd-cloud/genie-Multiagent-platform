package com.jd.genie.platform.phase2contract.dto;

import java.util.List;

public record AgentRuntimeSkill(
    String skillId,
    long skillVersion,
    int sortOrder,
    String instruction,
    String outputRequirement,
    String skillKey,
    String packageMode,
    String packageVersion,
    String packageHash,
    List<String> capabilityKeys,
    List<SkillEntrypointView> entrypoints
) {
    public AgentRuntimeSkill {
        capabilityKeys = capabilityKeys == null ? List.of() : List.copyOf(capabilityKeys);
        entrypoints = entrypoints == null ? List.of() : List.copyOf(entrypoints);
    }

    /** Backward-compatible 5-arg constructor for existing Fake/tests. */
    public AgentRuntimeSkill(
        String skillId,
        long skillVersion,
        int sortOrder,
        String instruction,
        String outputRequirement
    ) {
        this(
            skillId,
            skillVersion,
            sortOrder,
            instruction,
            outputRequirement,
            null,
            null,
            null,
            null,
            List.of(),
            List.of()
        );
    }
}
