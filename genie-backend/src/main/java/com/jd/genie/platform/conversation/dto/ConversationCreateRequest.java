package com.jd.genie.platform.conversation.dto;

public record ConversationCreateRequest(String title, Boolean privacyMode) {
    public ConversationCreateRequest(String title) {
        this(title, false);
    }

    public boolean privacyModeEnabled() {
        return Boolean.TRUE.equals(privacyMode);
    }
}
