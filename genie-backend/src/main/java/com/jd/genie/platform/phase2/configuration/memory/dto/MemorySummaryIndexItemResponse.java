package com.jd.genie.platform.phase2.configuration.memory.dto;

public record MemorySummaryIndexItemResponse(
    String conversationId,
    String path,
    String updatedAt,
    Long lastSummarizedTurnNo
) {
}
