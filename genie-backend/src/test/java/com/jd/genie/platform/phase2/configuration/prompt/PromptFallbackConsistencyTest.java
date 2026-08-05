package com.jd.genie.platform.phase2.configuration.prompt;

import com.jd.genie.agent.prompt.PlanningPrompt;
import com.jd.genie.agent.prompt.ToolCallPrompt;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptFallbackConsistencyTest {
    @Test
    void applicationAndFallbackPlannerPromptsShareCoreSemantics() {
        Properties properties = V1PromptTestSupport.loadApplicationProperties();
        String configuredSystem = V1PromptTestSupport.defaultPrompt(properties, V1PromptTestSupport.PLANNER_SYSTEM);
        String configuredNext = V1PromptTestSupport.defaultPrompt(properties, V1PromptTestSupport.PLANNER_NEXT);

        assertPlannerSemantics(configuredSystem + configuredNext);
        assertPlannerSemantics(PlanningPrompt.SYSTEM_PROMPT + PlanningPrompt.NEXT_STEP_PROMPT);
    }

    @Test
    void applicationAndFallbackToolPromptsShareCoreSemantics() {
        Properties properties = V1PromptTestSupport.loadApplicationProperties();
        String configuredExecutor = V1PromptTestSupport.defaultPrompt(properties, V1PromptTestSupport.EXECUTOR_SYSTEM)
            + V1PromptTestSupport.defaultPrompt(properties, V1PromptTestSupport.EXECUTOR_NEXT);
        String configuredReact = V1PromptTestSupport.defaultPrompt(properties, V1PromptTestSupport.REACT_SYSTEM)
            + V1PromptTestSupport.defaultPrompt(properties, V1PromptTestSupport.REACT_NEXT);
        String fallback = ToolCallPrompt.SYSTEM_PROMPT + ToolCallPrompt.NEXT_STEP_PROMPT;

        assertToolSemantics(configuredExecutor);
        assertToolSemantics(configuredReact);
        assertToolSemantics(fallback);
    }

    private static void assertPlannerSemantics(String prompt) {
        assertTrue(prompt.contains("general-purpose"));
        assertTrue(prompt.contains("Prefer the user language"));
        assertTrue(prompt.contains("mark_step"));
        assertTrue(prompt.contains("finish"));
        assertTrue(prompt.contains("not_started"));
        assertTrue(prompt.contains("in_progress"));
        assertTrue(prompt.contains("completed"));
        assertTrue(prompt.contains("Do not output internal reasoning") || prompt.contains("Do not reveal internal reasoning"));
        assertFalse(prompt.contains("Function call 2"));
    }

    private static void assertToolSemantics(String prompt) {
        assertTrue(prompt.contains("general-purpose"));
        assertTrue(prompt.contains("Use tools only when"));
        assertTrue(prompt.contains("Zero tool calls are valid") || prompt.contains("If no suitable tool exists"));
        assertTrue(prompt.contains("Tool Schema"));
        assertTrue(prompt.contains("[Function Calling]"));
        assertTrue(prompt.contains("Finish[answer]"));
        assertTrue(prompt.contains("Do not output internal reasoning"));
        assertFalse(prompt.contains("default to HTML reports"));
    }
}
