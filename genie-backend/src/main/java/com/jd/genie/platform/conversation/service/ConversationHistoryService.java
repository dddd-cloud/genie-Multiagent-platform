package com.jd.genie.platform.conversation.service;

import com.jd.genie.platform.contract.ConversationHistoryItem;
import com.jd.genie.platform.contract.ConversationMessageRole;
import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.conversation.exception.ConversationException;
import com.jd.genie.platform.conversation.history.CompletedHistoryTurnRow;
import com.jd.genie.platform.conversation.mapper.ConversationMapper;
import com.jd.genie.platform.conversation.mapper.ConversationMessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ConversationHistoryService {
    private final ConversationMapper conversationMapper;
    private final ConversationMessageMapper conversationMessageMapper;

    public List<ConversationHistoryItem> loadCompletedHistory(CurrentUser currentUser, String conversationId,
                                                              String excludeRequestId, int maxTurns,
                                                              int maxCharacters) {
        CurrentUser validUser = validateCurrentUser(currentUser);
        String validConversationId = trim(conversationId);
        String validExcludeRequestId = trim(excludeRequestId);
        if (isBlank(validConversationId) || maxTurns <= 0 || maxCharacters < 0) {
            throw validationError();
        }

        try {
            if (conversationMapper.selectOwnedConversation(
                validUser.tenantId(), validUser.userId(), validConversationId) == null) {
                throw error(MvpErrorCode.RESOURCE_NOT_FOUND, "Resource not found");
            }

            List<CompletedHistoryTurnRow> candidates = conversationMessageMapper.selectCompletedHistoryTurns(
                validUser.tenantId(), validUser.userId(), validConversationId, validExcludeRequestId, maxTurns);
            List<CompletedHistoryTurnRow> selected = selectWithinCharacterLimit(candidates, maxTurns, maxCharacters);
            selected.sort(Comparator.comparing(CompletedHistoryTurnRow::getTurnNo));
            return toHistoryItems(selected);
        } catch (ConversationException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            throw error(MvpErrorCode.DATABASE_UNAVAILABLE, "Database unavailable", exception);
        }
    }

    private List<CompletedHistoryTurnRow> selectWithinCharacterLimit(List<CompletedHistoryTurnRow> candidates,
                                                                     int maxTurns, int maxCharacters) {
        List<CompletedHistoryTurnRow> selected = new ArrayList<>();
        int totalCharacters = 0;
        for (CompletedHistoryTurnRow candidate : candidates) {
            if (selected.size() >= maxTurns) {
                break;
            }
            int turnCharacters = length(candidate.getUserContent()) + length(candidate.getAssistantContent());
            if (totalCharacters + turnCharacters > maxCharacters) {
                break;
            }
            selected.add(candidate);
            totalCharacters += turnCharacters;
        }
        return selected;
    }

    private List<ConversationHistoryItem> toHistoryItems(List<CompletedHistoryTurnRow> turns) {
        List<ConversationHistoryItem> items = new ArrayList<>(turns.size() * 2);
        for (CompletedHistoryTurnRow turn : turns) {
            items.add(new ConversationHistoryItem(turn.getTurnNo(), ConversationMessageRole.USER, content(turn.getUserContent())));
            items.add(new ConversationHistoryItem(turn.getTurnNo(), ConversationMessageRole.ASSISTANT, content(turn.getAssistantContent())));
        }
        return items;
    }

    private int length(String content) {
        return content == null ? 0 : content.length();
    }

    private String content(String content) {
        return content == null ? "" : content;
    }

    private CurrentUser validateCurrentUser(CurrentUser currentUser) {
        if (currentUser == null || isBlank(currentUser.tenantId()) || isBlank(currentUser.userId())) {
            throw validationError();
        }
        return currentUser;
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private ConversationException validationError() {
        return error(MvpErrorCode.VALIDATION_ERROR, "Invalid request");
    }

    private ConversationException error(MvpErrorCode code, String message) {
        return new ConversationException(code, message);
    }

    private ConversationException error(MvpErrorCode code, String message, Throwable cause) {
        return new ConversationException(code, message, cause);
    }
}