package com.jd.genie.platform.phase2.configuration.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.platform.phase2.configuration.agent.dto.AgentResponse;
import com.jd.genie.platform.phase2.configuration.skill.dto.SkillResponse;
import com.jd.genie.platform.phase2.configuration.prompt.PromptSkillFragmentView;

import java.time.Instant;
import java.util.List;

final class Phase2ApiAssembler {
    private final ObjectMapper objectMapper;

    Phase2ApiAssembler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    AgentView agent(AgentResponse response) {
        return new AgentView(
            response.id(),
            response.name(),
            response.description(),
            response.promptMode(),
            promptConfig(response.promptConfig()),
            response.systemPrompt(),
            response.modelName(),
            response.status(),
            response.version(),
            List.copyOf(response.skillIds()),
            List.copyOf(response.capabilityKeys()),
            response.createdAt(),
            response.updatedAt()
        );
    }

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

    private JsonNode promptConfig(String promptConfig) {
        if (promptConfig == null || promptConfig.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(promptConfig);
        } catch (JsonProcessingException ex) {
            return objectMapper.getNodeFactory().textNode(promptConfig);
        }
    }

    record AgentView(
        String id,
        String name,
        String description,
        String promptMode,
        JsonNode promptConfig,
        String systemPrompt,
        String modelName,
        String status,
        Long version,
        List<String> skillIds,
        List<String> capabilityKeys,
        Instant createdAt,
        Instant updatedAt
    ) {
        AgentView {
            skillIds = skillIds == null ? List.of() : List.copyOf(skillIds);
            capabilityKeys = capabilityKeys == null ? List.of() : List.copyOf(capabilityKeys);
        }
    }

    record SkillView(
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
        SkillView {
            capabilityKeys = capabilityKeys == null ? List.of() : List.copyOf(capabilityKeys);
        }
    }

    record PromptPreviewView(
        String compiledSystemPromptTemplate,
        List<PromptSkillFragmentView> skillFragments,
        String resolvedModelName,
        int codePointLength
    ) {
        PromptPreviewView {
            skillFragments = skillFragments == null ? List.of() : List.copyOf(skillFragments);
        }
    }
}
