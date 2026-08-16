package com.jd.genie.agent.llm;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.jd.genie.config.GenieConfig;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LlmSettingsResolverTest {
    @Test
    void parsesSnakeCaseApikeyAndBaseUrlFromLlmSettingsJson() {
        String json = """
            {"deepseek-v4-flash": {
                "model": "deepseek-v4-flash",
                "max_tokens": 20000,
                "temperature": 0,
                "base_url": "https://api.deepseek.com",
                "apikey": "sk-test",
                "interface_url": "/chat/completions",
                "max_input_tokens": 128000
            }}
            """;
        Map<String, LLMSettings> parsed = JSON.parseObject(json, new TypeReference<Map<String, LLMSettings>>() {
        });
        LLMSettings settings = parsed.get("deepseek-v4-flash");
        assertNotNull(settings);
        assertEquals("sk-test", settings.getApiKey());
        assertEquals("https://api.deepseek.com", settings.getBaseUrl());
        assertEquals("/chat/completions", settings.getInterfaceUrl());
        assertEquals(20000, settings.getMaxTokens());
    }

    @Test
    void fallsBackToAnyCompleteSettingsEntry() {
        GenieConfig config = mock(GenieConfig.class);
        LLMSettings settings = LLMSettings.builder()
            .model("actual-model")
            .apiKey("sk-test")
            .baseUrl("https://api.example")
            .build();
        when(config.getPlannerModelName()).thenReturn("missing");
        when(config.getReactModelName()).thenReturn("also-missing");
        when(config.getExecutorModelName()).thenReturn("still-missing");
        when(config.getLlmSettingsMap()).thenReturn(Map.of("actual-model", settings));

        LLMSettings resolved = LlmSettingsResolver.resolveComplete(config);
        assertNotNull(resolved);
        assertEquals("sk-test", resolved.getApiKey());
    }
}
