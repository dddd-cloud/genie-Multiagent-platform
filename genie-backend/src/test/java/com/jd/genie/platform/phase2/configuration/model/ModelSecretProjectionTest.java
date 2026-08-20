package com.jd.genie.platform.phase2.configuration.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.agent.llm.LLMSettings;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelSecretProjectionTest {

    @Test
    void catalogSerializationDoesNotExposeLlmSettingsSecrets() throws JsonProcessingException {
        ModelCatalogService service = new ModelCatalogService(ModelCatalogServiceTest.config("safe-model", Map.of(
            "safe-model", LLMSettings.builder()
                .model("provider-model")
                .apiKey("SECRET_MARKER")
                .baseUrl("https://secret.example")
                .interfaceUrl("/private")
                .extParams(Map.of("hidden", "value"))
                .build()
        )));

        String json = new ObjectMapper().writeValueAsString(service.listModels());

        assertTrue(json.contains("safe-model"));
        assertTrue(json.contains(ModelCatalogItem.MASKED_API_KEY));
        assertFalse(json.contains("SECRET_MARKER"));
        assertFalse(json.contains("\"apiKey\""));
        assertFalse(json.contains("extParams"));
        assertFalse(json.contains("hidden"));
    }
}
