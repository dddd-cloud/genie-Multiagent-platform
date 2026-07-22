package com.jd.genie.platform.contract;

public record ConversationHistoryItem(
    long turnNo,
    ConversationMessageRole role,
    String content
) {
}
