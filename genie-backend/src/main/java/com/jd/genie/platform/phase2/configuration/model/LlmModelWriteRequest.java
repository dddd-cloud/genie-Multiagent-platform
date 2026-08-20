package com.jd.genie.platform.phase2.configuration.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LlmModelWriteRequest(
    String name,
    String displayName,
    String model,
    String baseUrl,
    String interfaceUrl,
    Integer maxTokens,
    Double temperature,
    Integer maxInputTokens,
    String apiKey
) {
}
