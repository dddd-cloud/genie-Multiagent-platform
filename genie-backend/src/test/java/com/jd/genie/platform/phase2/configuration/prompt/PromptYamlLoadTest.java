package com.jd.genie.platform.phase2.configuration.prompt;

import com.alibaba.fastjson.JSON;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptYamlLoadTest {
    private static final Set<String> PROVIDED_PLACEHOLDERS = Set.of(
        "{{tools}}", "{{query}}", "{{date}}", "{{sopPrompt}}", "{{executorSopPrompt}}", "{{basePrompt}}",
        "{{files}}", "{{history_dialogue}}", "{{task}}"
    );

    @Test
    void applicationYamlLoadsAndPromptMapsContainRequiredDefaultKeys() {
        Properties properties = V1PromptTestSupport.loadApplicationProperties();

        assertTrue(V1PromptTestSupport.loadPromptMap(properties, V1PromptTestSupport.PLANNER_SYSTEM).containsKey("default"));
        assertTrue(V1PromptTestSupport.loadPromptMap(properties, V1PromptTestSupport.PLANNER_NEXT).containsKey("default"));
        assertTrue(V1PromptTestSupport.loadPromptMap(properties, V1PromptTestSupport.EXECUTOR_SYSTEM).containsKey("default"));
        assertTrue(V1PromptTestSupport.loadPromptMap(properties, V1PromptTestSupport.EXECUTOR_NEXT).containsKey("default"));
        assertTrue(V1PromptTestSupport.loadPromptMap(properties, V1PromptTestSupport.REACT_SYSTEM).containsKey("default"));
        assertTrue(V1PromptTestSupport.loadPromptMap(properties, V1PromptTestSupport.REACT_NEXT).containsKey("default"));
        JSON.parseObject(properties.getProperty(V1PromptTestSupport.STRUCT_PRE_POST));
    }

    @Test
    void promptPlaceholdersAreProvidedByExistingRuntimePaths() {
        Map<String, String> prompts = V1PromptTestSupport.productionPrompts();

        prompts.forEach((name, prompt) -> {
            List<String> placeholders = V1PromptTestSupport.unresolvedPlaceholders(prompt);
            assertTrue(PROVIDED_PLACEHOLDERS.containsAll(placeholders),
                () -> name + " contains unsupported placeholders: " + placeholders);
        });
    }

    @Test
    void formattingWithRuntimeValuesDoesNotLeaveTemplateMarkers() {
        String rendered = V1PromptTestSupport.allProductionPromptText()
            .replace("{{tools}}", "tool name: lookup; schema: {}")
            .replace("{{query}}", "Write a short project update email.")
            .replace("{{date}}", "2026-08-05")
            .replace("{{sopPrompt}}", "")
            .replace("{{executorSopPrompt}}", "")
            .replace("{{basePrompt}}", "base rules")
            .replace("{{files}}", "[]")
            .replace("{{history_dialogue}}", "[]")
            .replace("{{task}}", "Explain a Java null pointer risk.");

        assertEquals(List.of(), V1PromptTestSupport.unresolvedPlaceholders(rendered));
        assertTrue(rendered.contains("tool name: lookup"));
    }
}
