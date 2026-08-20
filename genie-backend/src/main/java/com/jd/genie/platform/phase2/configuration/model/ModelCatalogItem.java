package com.jd.genie.platform.phase2.configuration.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ModelCatalogItem(
    String id,
    String name,
    String displayName,
    String model,
    String baseUrl,
    String interfaceUrl,
    Integer maxTokens,
    Double temperature,
    Integer maxInputTokens,
    boolean apiKeyConfigured,
    String apiKeyMasked,
    boolean isDefault,
    boolean available,
    String source
) {
    public static final String MASKED_API_KEY = "••••••••";

    public ModelCatalogItem(String name, String displayName, boolean isDefault, boolean available) {
        this(
            name,
            name,
            displayName,
            null,
            null,
            null,
            null,
            null,
            null,
            false,
            null,
            isDefault,
            available,
            "env"
        );
    }
}
