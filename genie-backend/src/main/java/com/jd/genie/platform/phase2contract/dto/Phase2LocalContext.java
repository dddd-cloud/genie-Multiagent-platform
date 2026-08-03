package com.jd.genie.platform.phase2contract.dto;

public record Phase2LocalContext(
    int schemaVersion,
    String longTermMemory,
    String conversationSummary
) {
}
