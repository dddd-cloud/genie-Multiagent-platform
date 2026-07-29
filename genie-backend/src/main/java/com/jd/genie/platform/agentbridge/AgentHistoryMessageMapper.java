package com.jd.genie.platform.agentbridge;

import com.jd.genie.agent.dto.Message;
import com.jd.genie.model.req.AgentRequest;
import com.jd.genie.platform.contract.ConversationHistoryItem;
import com.jd.genie.platform.contract.ConversationMessageRole;

import java.util.List;
import java.util.Objects;

public final class AgentHistoryMessageMapper {

    public List<AgentRequest.Message> toAgentRequestMessages(List<ConversationHistoryItem> history) {
        return toAgentRequestMessages(history, null);
    }

    public List<AgentRequest.Message> toAgentRequestMessages(
            List<ConversationHistoryItem> history,
            String currentQuery
    ) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }

        return history.stream()
                .filter(Objects::nonNull)
                .filter(item -> item.role() != null)
                .filter(item -> hasText(item.content()))
                .filter(item -> !isCurrentQuery(item, currentQuery))
                .map(this::toAgentRequestMessage)
                .toList();
    }

    public List<Message> toMemoryMessages(List<AgentRequest.Message> history) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }

        return history.stream()
                .filter(Objects::nonNull)
                .filter(item -> hasText(item.getContent()))
                .map(this::toMemoryMessage)
                .filter(Objects::nonNull)
                .toList();
    }

    private AgentRequest.Message toAgentRequestMessage(ConversationHistoryItem item) {
        return AgentRequest.Message.builder()
                .role(toAgentRole(item.role()))
                .content(item.content())
                .build();
    }

    private Message toMemoryMessage(AgentRequest.Message item) {
        if ("user".equals(item.getRole())) {
            return Message.userMessage(item.getContent(), null);
        }
        if ("assistant".equals(item.getRole())) {
            return Message.assistantMessage(item.getContent(), null);
        }
        return null;
    }

    private String toAgentRole(ConversationMessageRole role) {
        return switch (role) {
            case USER -> "user";
            case ASSISTANT -> "assistant";
        };
    }

    private boolean isCurrentQuery(ConversationHistoryItem item, String currentQuery) {
        return item.role() == ConversationMessageRole.USER
                && hasText(currentQuery)
                && currentQuery.equals(item.content());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
