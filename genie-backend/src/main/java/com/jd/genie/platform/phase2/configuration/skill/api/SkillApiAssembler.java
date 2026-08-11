package com.jd.genie.platform.phase2.configuration.skill.api;

import com.jd.genie.platform.phase2.configuration.skill.dto.SkillResponse;

import java.time.Instant;
import java.util.List;

final class SkillApiAssembler {

    SkillView skill(SkillResponse response) {
        return new SkillView(
            response.id(),
            response.name(),
            response.description(),
            response.instruction(),
            response.outputRequirement(),
            response.status(),
            response.version(),
            List.copyOf(response.capabilityKeys()),
            response.createdAt(),
            response.updatedAt()
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
        Instant updatedAt
    ) {
        public SkillView {
            capabilityKeys = capabilityKeys == null ? List.of() : List.copyOf(capabilityKeys);
        }
    }
}
