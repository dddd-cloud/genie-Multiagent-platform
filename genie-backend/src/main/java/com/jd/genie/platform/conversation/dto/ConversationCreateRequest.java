package com.jd.genie.platform.conversation.dto;

public record ConversationCreateRequest(String title, Boolean privacyMode, String workspaceId) {
    public ConversationCreateRequest(String title) {
        this(title, false, null);
    }

    public boolean privacyModeEnabled() {
        return Boolean.TRUE.equals(privacyMode);
    }
}
