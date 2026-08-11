package com.jd.genie.platform.phase2contract.dto;

import com.jd.genie.platform.phase2contract.enums.SkillPackageMode;

import java.util.List;

public record SkillRuntimePackage(
    String skillId,
    long skillVersion,
    int sortOrder,
    String status,
    String skillKey,
    String name,
    String description,
    SkillPackageMode packageMode,
    String packageVersion,
    String packageHash,
    String instructionMarkdown,
    String outputRequirement,
    List<String> resourceManifest,
    List<SkillEntrypointView> entrypoints,
    List<String> capabilityKeys
) {
    public SkillRuntimePackage {
        resourceManifest = resourceManifest == null ? List.of() : List.copyOf(resourceManifest);
        entrypoints = entrypoints == null ? List.of() : List.copyOf(entrypoints);
        capabilityKeys = capabilityKeys == null ? List.of() : List.copyOf(capabilityKeys);
    }
}
