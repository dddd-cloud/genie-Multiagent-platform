package com.jd.genie.platform.phase2.configuration.memory.dto;

public record ConversationSummaryTurn(
    Long turnNo,
    String userMessage,
    String assistantMessage,
    String assistantStatus
) {
}
