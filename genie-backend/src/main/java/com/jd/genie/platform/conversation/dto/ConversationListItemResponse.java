package com.jd.genie.platform.conversation.dto;

import java.time.Instant;

public record ConversationListItemResponse(
    String id,
    String title,
    boolean privacyMode,
    String workspaceId,
    Instant lastMessageAt,
    Instant createdAt,
    Instant updatedAt,
    String lastMessagePreview
) {
}
