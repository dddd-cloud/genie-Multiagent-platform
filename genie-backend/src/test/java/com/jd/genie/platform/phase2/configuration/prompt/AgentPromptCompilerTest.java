package com.jd.genie.platform.phase2.configuration.prompt;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentPromptCompilerTest {
    private final AgentPromptCompiler compiler = new AgentPromptCompiler();

    @Test
    void compilesStructuredFieldsAndEnabledSkillsInStableOrder() {
        PromptCompilationResult result = compiler.compile(new PromptCompilationRequest("STRUCTURED",
            "{\"objective\":\"Do research\",\"role\":\"Assistant\"}", "forged frontend prompt",
            List.of(
                new PromptSkillFragment("s2", 1L, "Second", 2, "Use second", "Second output"),
                new PromptSkillFragment("s1", 1L, "First", 1, "Use first", "First output")
            )));

        assertEquals("STRUCTURED", result.promptMode());
        assertEquals("{\"role\":\"Assistant\",\"objective\":\"Do research\"}", result.canonicalPromptConfig());
        assertTrue(result.compiledSystemPromptTemplate().contains("## role"));
        assertTrue(result.compiledSystemPromptTemplate().contains("## objective"));
        assertTrue(result.compiledSystemPromptTemplate().indexOf("Skill 1: First")
            < result.compiledSystemPromptTemplate().indexOf("Skill 2: Second"));
        assertTrue(result.compiledSystemPromptTemplate().contains("{{tools}}"));
    }

    @Test
    void compilesRawAndStoresNullCanonicalConfig() {
        PromptCompilationResult result = compiler.compile(new PromptCompilationRequest("RAW", "{\"objective\":\"ignored\"}",
            "Keep this raw prompt", List.of()));

        assertEquals("RAW", result.promptMode());
        assertNull(result.canonicalPromptConfig());
        assertTrue(result.compiledSystemPromptTemplate().contains("Keep this raw prompt"));
    }
}
