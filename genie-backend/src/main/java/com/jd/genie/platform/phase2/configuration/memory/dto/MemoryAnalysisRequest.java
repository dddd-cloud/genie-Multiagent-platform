package com.jd.genie.platform.phase2.configuration.memory.dto;

public record MemoryAnalysisRequest(
    String conversationId,
    String userMessage,
    String assistantMessage,
    String currentLongTermMemory,
    String turnStatus
) {
}
