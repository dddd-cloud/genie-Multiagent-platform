package com.jd.genie.platform.conversation.dto;

public record ConversationAttachmentResponse(
    String id,
    String fileName,
    String fileType,
    long sizeBytes,
    int extractedChars,
    boolean truncated
) {
}
