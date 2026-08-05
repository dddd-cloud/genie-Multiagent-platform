package com.jd.genie.platform.phase2.configuration.prompt;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class V1PromptScenarioRegressionTest {
    @Test
    void writingTaskCanBeAnsweredDirectlyWithoutToolsOrFiles() {
        String prompt = V1PromptTestSupport.allProductionPromptText();

        assertTrue(prompt.contains("Ordinary Q&A, writing"));
        assertTrue(prompt.contains("directly in chat") || prompt.contains("answer directly"));
        assertTrue(prompt.contains("Create files only when"));
        assertFalse(prompt.contains("writing tasks must use search"));
    }

    @Test
    void codeExplanationIsNotForcedIntoSearchReportOrReasoningOutput() {
        String prompt = V1PromptTestSupport.allProductionPromptText();

        assertTrue(prompt.contains("code explanation"));
        assertTrue(prompt.contains("Do not output internal reasoning"));
        assertFalse(prompt.contains("code explanation must search"));
        assertFalse(prompt.contains("code explanation must create report"));
    }

    @Test
    void travelFileResearchAndDataTasksRemainTaskDriven() {
        Map<String, String> prompts = V1PromptTestSupport.productionPrompts();
        String prompt = String.join("\n", prompts.values());

        assertTrue(prompt.contains("travel planning"));
        assertTrue(prompt.contains("file summary"));
        assertTrue(prompt.contains("research"));
        assertTrue(prompt.contains("data-processing"));
        assertTrue(prompt.contains("Multiple calls are only for dependencies, verification, or missing required information"));
        assertFalse(prompt.contains("3-5 searches"));
        assertFalse(prompt.contains("investor sentiment dimension"));
    }

    @Test
    void emptyOrMissingToolsRemainValid() {
        String prompt = V1PromptTestSupport.allProductionPromptText()
            .replace("{{tools}}", "")
            .replace("{{files}}", "[]")
            .replace("{{query}}", "Summarize the uploaded document conclusions.")
            .replace("{{date}}", "2026-08-05")
            .replace("{{basePrompt}}", "")
            .replace("{{sopPrompt}}", "")
            .replace("{{executorSopPrompt}}", "")
            .replace("{{history_dialogue}}", "[]")
            .replace("{{task}}", "Summarize the uploaded document conclusions.");

        assertTrue(prompt.contains("Zero tool calls are valid"));
        assertTrue(prompt.contains("If no suitable tool exists"));
        assertFalse(prompt.contains("{{"));
    }
}
