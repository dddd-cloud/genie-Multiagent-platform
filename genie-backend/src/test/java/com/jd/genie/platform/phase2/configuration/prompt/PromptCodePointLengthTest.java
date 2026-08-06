package com.jd.genie.platform.phase2.configuration.prompt;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptCodePointLengthTest {
    private final AgentPromptCompiler compiler = new AgentPromptCompiler();

    @Test
    void countsEmojiAsSingleCodePoint() {
        PromptCompilationResult result = compiler.compile(new PromptCompilationRequest("RAW", null, "😀", List.of()));

        assertTrue(result.compiledSystemPromptTemplate().contains("😀"));
        assertTrue(result.codePointLength() < result.compiledSystemPromptTemplate().length());
    }

    @Test
    void rejectsCompiledPromptOverLimit() {
        String tooLong = "a".repeat(AgentPromptCompiler.MAX_COMPILED_PROMPT_CODE_POINTS + 1);

        assertThrows(PromptValidationException.class,
            () -> compiler.compile(new PromptCompilationRequest("RAW", null, tooLong, List.of())));
    }
}
