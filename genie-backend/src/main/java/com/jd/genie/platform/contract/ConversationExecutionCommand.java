package com.jd.genie.platform.contract;

public record ConversationExecutionCommand(
    String conversationId,
    String requestId,
    String query,
    Integer deepThink,
    String outputStyle
) {
}
