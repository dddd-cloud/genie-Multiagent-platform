package com.jd.genie.platform.phase2.configuration.model;

import com.jd.genie.agent.llm.LLMSettings;
import com.jd.genie.config.GenieConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelCatalogServiceTest {

    @Test
    void listsSystemDefaultAndSortedModelKeysOnly() {
        ModelCatalogService service = new ModelCatalogService(config("a-model", Map.of(
            "z-model", LLMSettings.builder().model("secret-z").build(),
            "a-model", LLMSettings.builder().model("secret-a").build()
        )));

        List<ModelCatalogItem> models = service.listModels();

        assertEquals("system-default", models.get(0).name());
        assertTrue(models.get(0).isDefault());
        assertTrue(models.get(0).available());
        assertEquals("a-model", models.get(1).name());
        assertEquals("z-model", models.get(2).name());
    }

    @Test
    void resolvesSystemDefaultToNullStorageAndRejectsUnavailableDefault() {
        ModelCatalogService service = new ModelCatalogService(config("missing", Map.of("qwen-plus", LLMSettings.builder().build())));

        ModelResolutionResult result = service.resolveForStorage("system-default");

        assertNull(result.storedModelName());
        assertEquals("missing", result.resolvedModelName());
        assertFalse(result.available());
    }

    @Test
    void treatsCatalogDefaultAsSystemDefaultWhenItIsNotAConfiguredModel() {
        ModelCatalogService service = new ModelCatalogService(config("qwen-plus", Map.of(
            "qwen-plus", LLMSettings.builder().build()
        )));

        ModelResolutionResult result = service.resolveForStorage("default");

        assertNull(result.storedModelName());
        assertEquals("qwen-plus", result.resolvedModelName());
        assertTrue(result.available());
    }

    @Test
    void keepsLiteralDefaultWhenItIsAConfiguredModel() {
        ModelCatalogService service = new ModelCatalogService(config("qwen-plus", Map.of(
            "default", LLMSettings.builder().build(),
            "qwen-plus", LLMSettings.builder().build()
        )));

        ModelResolutionResult result = service.resolveForStorage("default");

        assertEquals("default", result.storedModelName());
        assertTrue(result.available());
    }

    static GenieConfig config(String reactModelName, Map<String, LLMSettings> settings) {
        return new GenieConfig() {
            @Override
            public Map<String, LLMSettings> getLlmSettingsMap() {
                return settings;
            }

            @Override
            public String getReactModelName() {
                return reactModelName;
            }
        };
    }
}
