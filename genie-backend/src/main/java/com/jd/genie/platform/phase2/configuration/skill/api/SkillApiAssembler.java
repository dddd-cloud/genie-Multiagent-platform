package com.jd.genie.platform.phase2.configuration.skill.api;

import com.jd.genie.platform.phase2.configuration.skill.dto.SkillResponse;
import com.jd.genie.platform.phase2contract.dto.SkillEntrypointView;

import java.time.Instant;
import java.util.List;

final class SkillApiAssembler {

    SkillView skill(SkillResponse response) {
        return new SkillView(
            response.id(),
            response.name(),
            response.description(),
            response.instruction(),
            response.outputRequirement() == null ? "" : response.outputRequirement(),
            response.status(),
            response.version(),
            List.copyOf(response.capabilityKeys()),
            response.createdAt(),
            response.updatedAt(),
            response.packageMode(),
            response.packageHash(),
            response.entrypoints() == null ? List.of() : List.copyOf(response.entrypoints())
        );
    }

    public record SkillView(
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
        public SkillView {
            capabilityKeys = capabilityKeys == null ? List.of() : List.copyOf(capabilityKeys);
            entrypoints = entrypoints == null ? List.of() : List.copyOf(entrypoints);
            outputRequirement = outputRequirement == null ? "" : outputRequirement;
            description = description == null ? "" : description;
        }
    }
}
