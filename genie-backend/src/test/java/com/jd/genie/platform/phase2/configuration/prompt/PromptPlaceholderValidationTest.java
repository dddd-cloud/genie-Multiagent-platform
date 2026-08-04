package com.jd.genie.platform.phase2.configuration.prompt;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PromptPlaceholderValidationTest {
    private final AgentPromptCompiler compiler = new AgentPromptCompiler();

    @Test
    void acceptsFrozenPlaceholdersAndPlainJsonBraces() {
        assertDoesNotThrow(() -> compiler.validatePlaceholders("Use {{tools}} and {{query}} on {\"plain\":true}"));
    }

    @Test
    void rejectsUnknownCaseMismatchedAndUnclosedPlaceholders() {
        assertThrows(PromptValidationException.class, () -> compiler.validatePlaceholders("{{password}}"));
        assertThrows(PromptValidationException.class, () -> compiler.validatePlaceholders("{{TOOLS}}"));
        assertThrows(PromptValidationException.class, () -> compiler.validatePlaceholders("{{unknown.value}}"));
        assertThrows(PromptValidationException.class, () -> compiler.validatePlaceholders("{{"));
    }

    @Test
    void scansSkillsForUnknownPlaceholders() {
        assertThrows(PromptValidationException.class, () -> compiler.compile(new PromptCompilationRequest("RAW", null, "Prompt",
            List.of(new PromptSkillFragment("s1", 1L, "Skill", 1, "Instruction {{password}}", null)))));
    }
}
