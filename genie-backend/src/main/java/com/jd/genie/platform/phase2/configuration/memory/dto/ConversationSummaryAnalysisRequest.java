package com.jd.genie.platform.phase2.configuration.memory.dto;

import java.util.List;

public record ConversationSummaryAnalysisRequest(
    String conversationId,
    String currentSummary,
    List<ConversationSummaryTurn> newTurns
) {
    public ConversationSummaryAnalysisRequest {
        newTurns = newTurns == null ? List.of() : List.copyOf(newTurns);
    }
}
