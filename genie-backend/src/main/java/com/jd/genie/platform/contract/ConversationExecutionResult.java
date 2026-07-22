package com.jd.genie.platform.contract;

public record ConversationExecutionResult(
    String conversationId,
    String requestId,
    String userMessageId,
    String assistantMessageId,
    long turnNo
) {
}
