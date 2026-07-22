package com.jd.genie.platform.conversation.dto;

import java.time.Instant;

public record ConversationMessageResponse(
    String id,
    Long turnNo,
    String role,
    String status,
    String requestId,
    String content,
    String streamSnapshot,
    Integer payloadVersion,
    Integer deepThink,
    String outputStyle,
    String errorCode,
    String errorMessage,
    Instant createdAt,
    Instant updatedAt
) {
}
