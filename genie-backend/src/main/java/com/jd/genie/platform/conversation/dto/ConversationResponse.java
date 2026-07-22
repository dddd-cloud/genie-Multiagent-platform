package com.jd.genie.platform.conversation.dto;

import java.time.Instant;

public record ConversationResponse(
    String id,
    String title,
    Instant lastMessageAt,
    Instant createdAt,
    Instant updatedAt
) {
}
