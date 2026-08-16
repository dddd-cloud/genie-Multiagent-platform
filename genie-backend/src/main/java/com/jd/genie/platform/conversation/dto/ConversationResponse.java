package com.jd.genie.platform.conversation.dto;

import java.time.Instant;

public record ConversationResponse(
    String id,
    String title,
    boolean privacyMode,
    Instant lastMessageAt,
    Instant createdAt,
    Instant updatedAt
) {
}
