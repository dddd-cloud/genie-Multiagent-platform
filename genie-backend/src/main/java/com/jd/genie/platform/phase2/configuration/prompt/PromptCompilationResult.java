package com.jd.genie.platform.phase2.configuration.prompt;

public record PromptCompilationResult(
    String promptMode,
    String canonicalPromptConfig,
    String compiledSystemPromptTemplate,
    int codePointLength
) {
}
