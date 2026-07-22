package com.jd.genie.platform.conversation.service;

import com.jd.genie.platform.contract.ConversationExecutionCommand;
import com.jd.genie.platform.contract.ConversationExecutionPort;
import com.jd.genie.platform.contract.ConversationExecutionResult;
import com.jd.genie.platform.contract.ConversationHistoryItem;
import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.MessageCompletionCommand;
import com.jd.genie.platform.contract.MessageFailureCommand;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.conversation.entity.ConversationEntity;
import com.jd.genie.platform.conversation.entity.ConversationMessageEntity;
import com.jd.genie.platform.conversation.exception.ConversationException;
import com.jd.genie.platform.conversation.mapper.ConversationMapper;
import com.jd.genie.platform.conversation.mapper.ConversationMessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConversationExecutionService implements ConversationExecutionPort {
    private static final String DEFAULT_TITLE = "新对话";
    private static final int MAX_QUERY_CODE_POINTS = 20_000;
    private static final int MAX_REQUEST_ID_LENGTH = 64;
    private static final int TITLE_CODE_POINTS = 30;
    private static final int PAYLOAD_VERSION = 1;
    private static final List<String> OUTPUT_STYLES = List.of("dataAgent", "html", "docs", "ppt", "table");

    private final ConversationMapper conversationMapper;
    private final ConversationMessageMapper conversationMessageMapper;
    private final Clock clock = Clock.systemUTC();

    @Override
    @Transactional
    public ConversationExecutionResult prepareExecution(CurrentUser currentUser, ConversationExecutionCommand command) {
        ValidatedCommand valid = validate(currentUser, command);
        ConversationEntity locked = conversationMapper.selectOwnedConversationForUpdate(
            currentUser.tenantId(), currentUser.userId(), valid.conversationId());
        if (locked == null) {
            throw error(MvpErrorCode.RESOURCE_NOT_FOUND, "资源不存在");
        }

        if (conversationMessageMapper.existsRequestId(
            currentUser.tenantId(), currentUser.userId(), valid.conversationId(), valid.requestId())) {
            throw error(MvpErrorCode.DUPLICATE_REQUEST, "请求已存在");
        }
        if (conversationMessageMapper.existsActiveAssistant(
            currentUser.tenantId(), currentUser.userId(), valid.conversationId())) {
            throw error(MvpErrorCode.CONVERSATION_BUSY, "当前会话正在执行，请稍后再试");
        }

        long turnNo = locked.getNextTurnNo();
        Instant now = Instant.now(clock);
        String userMessageId = UUID.randomUUID().toString();
        String assistantMessageId = UUID.randomUUID().toString();

        try {
            conversationMessageMapper.insert(userMessage(
                userMessageId, valid, turnNo, now));
            conversationMessageMapper.insert(assistantMessage(
                assistantMessageId, valid, turnNo, now));
            int updated = conversationMapper.completePrepareExecution(
                currentUser.tenantId(),
                currentUser.userId(),
                valid.conversationId(),
                locked.getNextTurnNo(),
                now,
                now,
                generatedTitle(locked, valid.query(), turnNo)
            );
            if (updated != 1) {
                throw error(MvpErrorCode.DATABASE_UNAVAILABLE, "数据库暂不可用");
            }
        } catch (DuplicateKeyException exception) {
            if (isRequestRoleDuplicate(exception)) {
                throw error(MvpErrorCode.DUPLICATE_REQUEST, "请求已存在", exception);
            }
            throw exception;
        } catch (DataAccessException exception) {
            throw error(MvpErrorCode.DATABASE_UNAVAILABLE, "数据库暂不可用", exception);
        }

        return new ConversationExecutionResult(
            valid.conversationId(), valid.requestId(), userMessageId, assistantMessageId, turnNo);
    }

    @Override
    public void markStreaming(CurrentUser currentUser, String assistantMessageId) {
        throw new UnsupportedOperationException("markStreaming is not implemented in MVP-B phase 3");
    }

    @Override
    public void complete(CurrentUser currentUser, MessageCompletionCommand command) {
        throw new UnsupportedOperationException("complete is not implemented in MVP-B phase 3");
    }

    @Override
    public void fail(CurrentUser currentUser, MessageFailureCommand command) {
        throw new UnsupportedOperationException("fail is not implemented in MVP-B phase 3");
    }

    @Override
    public void interrupt(CurrentUser currentUser, MessageFailureCommand command) {
        throw new UnsupportedOperationException("interrupt is not implemented in MVP-B phase 3");
    }

    @Override
    public List<ConversationHistoryItem> loadCompletedHistory(CurrentUser currentUser, String conversationId,
                                                              String excludeRequestId, int maxTurns,
                                                              int maxCharacters) {
        throw new UnsupportedOperationException("loadCompletedHistory is not implemented in MVP-B phase 3");
    }

    private ValidatedCommand validate(CurrentUser currentUser, ConversationExecutionCommand command) {
        if (currentUser == null || isBlank(currentUser.tenantId()) || isBlank(currentUser.userId()) || command == null) {
            throw validationError();
        }
        String conversationId = trim(command.conversationId());
        String requestId = trim(command.requestId());
        String query = trim(command.query());
        if (isBlank(conversationId) || isBlank(requestId) || requestId.length() > MAX_REQUEST_ID_LENGTH) {
            throw validationError();
        }
        if (isBlank(query) || query.codePointCount(0, query.length()) > MAX_QUERY_CODE_POINTS) {
            throw validationError();
        }
        int deepThink = command.deepThink() == null ? 0 : command.deepThink();
        if (deepThink != 0 && deepThink != 1) {
            throw validationError();
        }
        String outputStyle = isBlank(command.outputStyle()) ? "docs" : command.outputStyle().trim();
        if (!OUTPUT_STYLES.contains(outputStyle)) {
            throw validationError();
        }
        return new ValidatedCommand(conversationId, requestId, query, deepThink, outputStyle);
    }

    private ConversationMessageEntity userMessage(String id, ValidatedCommand command, long turnNo, Instant now) {
        ConversationMessageEntity message = baseMessage(id, command, turnNo, now);
        message.setRole("USER");
        message.setStatus("COMPLETED");
        message.setContent(command.query());
        return message;
    }

    private ConversationMessageEntity assistantMessage(String id, ValidatedCommand command, long turnNo, Instant now) {
        ConversationMessageEntity message = baseMessage(id, command, turnNo, now);
        message.setRole("ASSISTANT");
        message.setStatus("PENDING");
        return message;
    }

    private ConversationMessageEntity baseMessage(String id, ValidatedCommand command, long turnNo, Instant now) {
        ConversationMessageEntity message = new ConversationMessageEntity();
        message.setId(id);
        message.setConversationId(command.conversationId());
        message.setTurnNo(turnNo);
        message.setRequestId(command.requestId());
        message.setPayloadVersion(PAYLOAD_VERSION);
        message.setDeepThink(command.deepThink());
        message.setOutputStyle(command.outputStyle());
        message.setCreatedAt(now);
        message.setUpdatedAt(now);
        return message;
    }

    private String generatedTitle(ConversationEntity locked, String query, long turnNo) {
        if (turnNo != 1 || !DEFAULT_TITLE.equals(locked.getTitle())) {
            return null;
        }
        String normalized = query.trim().replaceAll("\\s+", " ");
        if (normalized.isEmpty()) {
            return DEFAULT_TITLE;
        }
        int codePoints = normalized.codePointCount(0, normalized.length());
        if (codePoints <= TITLE_CODE_POINTS) {
            return normalized;
        }
        return normalized.substring(0, normalized.offsetByCodePoints(0, TITLE_CODE_POINTS));
    }

    private boolean isRequestRoleDuplicate(DuplicateKeyException exception) {
        String message = exception.getMostSpecificCause() == null
            ? exception.getMessage()
            : exception.getMostSpecificCause().getMessage();
        return message != null && message.contains("uk_msg_request_role");
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private ConversationException validationError() {
        return error(MvpErrorCode.VALIDATION_ERROR, "请求参数不合法");
    }

    private ConversationException error(MvpErrorCode code, String message) {
        return new ConversationException(code, message);
    }

    private ConversationException error(MvpErrorCode code, String message, Throwable cause) {
        return new ConversationException(code, message, cause);
    }

    private record ValidatedCommand(
        String conversationId,
        String requestId,
        String query,
        Integer deepThink,
        String outputStyle
    ) {
    }
}
