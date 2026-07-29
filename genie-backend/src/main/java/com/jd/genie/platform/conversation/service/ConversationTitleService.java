package com.jd.genie.platform.conversation.service;

import com.jd.genie.platform.conversation.mapper.ConversationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ConversationTitleService {
    public static final String DEFAULT_TITLE = "新对话";
    private static final int TITLE_CODE_POINTS = 30;

    private final ConversationMapper conversationMapper;

    public void autoTitleFirstTurn(String tenantId, String ownerId, String conversationId, long turnNo,
                                   String query, Instant updatedAt) {
        if (turnNo != 1) {
            return;
        }
        conversationMapper.autoTitleFirstTurnIfDefault(
            tenantId,
            ownerId,
            conversationId,
            turnNo + 1,
            DEFAULT_TITLE,
            generateTitle(query),
            updatedAt
        );
    }

    public String generateTitle(String query) {
        String normalized = query == null ? "" : query.replace('\n', ' ').replace('\r', ' ');
        normalized = normalized.replaceAll("\\s+", " ").trim();
        if (normalized.isEmpty()) {
            return DEFAULT_TITLE;
        }
        int codePoints = normalized.codePointCount(0, normalized.length());
        if (codePoints <= TITLE_CODE_POINTS) {
            return normalized;
        }
        return normalized.substring(0, normalized.offsetByCodePoints(0, TITLE_CODE_POINTS));
    }
}