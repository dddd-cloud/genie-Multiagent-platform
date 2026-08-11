package com.jd.genie.platform.phase2.configuration.agent.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.platform.phase2.configuration.agent.dto.AgentResponse;

import java.time.Instant;
import java.util.List;

final class AgentApiAssembler {
    private final ObjectMapper objectMapper;

    AgentApiAssembler(ObjectMapper objectMapper) {
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

    public record AgentView(
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
        public AgentView {
            skillIds = skillIds == null ? List.of() : List.copyOf(skillIds);
            capabilityKeys = capabilityKeys == null ? List.of() : List.copyOf(capabilityKeys);
        }
    }
}
