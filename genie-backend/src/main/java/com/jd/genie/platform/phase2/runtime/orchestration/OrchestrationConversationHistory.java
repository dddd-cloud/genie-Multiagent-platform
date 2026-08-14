package com.jd.genie.platform.phase2.runtime.orchestration;

import com.jd.genie.model.req.AgentRequest;

import java.util.List;

/**
 * Formats persisted conversation turns for orchestration prompts.
 * History is already bounded by GENIE_HISTORY_MAX_TURNS / GENIE_HISTORY_MAX_CHARACTERS at load time.
 */
public final class OrchestrationConversationHistory {
    private OrchestrationConversationHistory() {
    }

    public static String format(List<AgentRequest.Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (AgentRequest.Message message : messages) {
            if (message == null || blank(message.getContent())) {
                continue;
            }
            String role = roleLabel(message.getRole());
            if (role == null) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(role).append(": ").append(message.getContent().trim());
        }
        return sb.toString();
    }

    private static String roleLabel(String role) {
        if ("user".equalsIgnoreCase(role)) {
            return "user";
        }
        if ("assistant".equalsIgnoreCase(role)) {
            return "assistant";
        }
        return null;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
