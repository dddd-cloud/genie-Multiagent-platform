package com.jd.genie.platform.phase2.configuration.model;

import java.time.Instant;

public record UserLlmModelRecord(
    String id,
    String tenantId,
    String ownerId,
    String name,
    String displayName,
    String model,
    String baseUrl,
    String interfaceUrl,
    int maxTokens,
    double temperature,
    int maxInputTokens,
    String apiKeyEnvelope,
    Instant createdAt,
    Instant updatedAt
) {
    boolean apiKeyConfigured() {
        return apiKeyEnvelope != null && !apiKeyEnvelope.isBlank();
    }
}
