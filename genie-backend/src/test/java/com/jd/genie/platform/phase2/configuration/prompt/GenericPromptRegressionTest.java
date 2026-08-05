package com.jd.genie.platform.phase2.configuration.prompt;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenericPromptRegressionTest {
    @Test
    void effectivePromptsKeepGeneralAgentBoundaries() {
        String promptText = V1PromptTestSupport.allProductionPromptText();

        assertTrue(promptText.contains("general-purpose"));
        assertTrue(promptText.contains("Prefer the user language"));
        assertTrue(promptText.contains("requested output format"));
        assertTrue(promptText.contains("Use tools only when"));
        assertTrue(promptText.contains("Tool Schema"));
        assertTrue(promptText.contains("Do not fabricate") || promptText.contains("Do not invent"));
        assertTrue(promptText.contains("limited and reasonable"));
        assertTrue(promptText.contains("Create files only when"));
        assertTrue(promptText.contains("Do not output internal reasoning"));
    }

    @Test
    void commonTaskScenariosRemainDomainNeutral() {
        Map<String, String> prompts = V1PromptTestSupport.productionPrompts();
        String combined = String.join("\n", prompts.values());

        assertTrue(combined.contains("writing"));
        assertTrue(combined.contains("code explanation"));
        assertTrue(combined.contains("travel planning"));
        assertTrue(combined.contains("file summary"));
        assertTrue(combined.contains("research"));
        assertTrue(combined.contains("data-processing"));
        assertFalse(combined.contains("must be converted into finance"));
        assertFalse(combined.contains("must create HTML"));
    }
}
