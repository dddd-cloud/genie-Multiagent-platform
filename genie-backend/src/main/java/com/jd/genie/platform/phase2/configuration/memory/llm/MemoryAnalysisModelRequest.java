package com.jd.genie.platform.phase2.configuration.memory.llm;

public record MemoryAnalysisModelRequest(
    String conversationId,
    String systemPrompt,
    String userPrompt,
    int timeoutMs
) {
}
