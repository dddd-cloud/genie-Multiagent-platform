package com.jd.genie.platform.conversation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.jd.genie.platform.conversation.snapshot.SnapshotValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ConversationExecutionService implements ConversationExecutionPort {
    private static final String DEFAULT_TITLE = "\u65b0\u5bf9\u8bdd";
    private static final int MAX_QUERY_CODE_POINTS = 20_000;
    private static final int MAX_REQUEST_ID_LENGTH = 64;
    private static final int MAX_ERROR_CODE_LENGTH = 64;
    private static final int MAX_ERROR_MESSAGE_LENGTH = 1_000;
    private static final int TITLE_CODE_POINTS = 30;
    private static final int PAYLOAD_VERSION = SnapshotValidator.PAYLOAD_VERSION;
    private static final List<String> OUTPUT_STYLES = List.of("dataAgent", "html", "docs", "ppt", "table");
    private static final ObjectMapper SNAPSHOT_OBJECT_MAPPER = new ObjectMapper();

    private final ConversationMapper conversationMapper;
    private final ConversationMessageMapper conversationMessageMapper;
    private final ConversationHistoryService conversationHistoryService;
    private final Clock clock = Clock.systemUTC();

    @Value("${GENIE_STREAM_SNAPSHOT_MAX_BYTES:8388608}")
    private int snapshotMaxBytes = 8_388_608;

    @Autowired
    public ConversationExecutionService(ConversationMapper conversationMapper,
                                        ConversationMessageMapper conversationMessageMapper,
                                        ConversationHistoryService conversationHistoryService) {
        this.conversationMapper = conversationMapper;
        this.conversationMessageMapper = conversationMessageMapper;
        this.conversationHistoryService = conversationHistoryService;
    }

    public ConversationExecutionService(ConversationMapper conversationMapper,
                                 ConversationMessageMapper conversationMessageMapper) {
        this(conversationMapper, conversationMessageMapper,
            new ConversationHistoryService(conversationMapper, conversationMessageMapper));
    }

    @Override
    @Transactional
    public ConversationExecutionResult prepareExecution(CurrentUser currentUser, ConversationExecutionCommand command) {
        ValidatedCommand valid = validate(currentUser, command);
        ConversationEntity locked = conversationMapper.selectOwnedConversationForUpdate(
            currentUser.tenantId(), currentUser.userId(), valid.conversationId());
        if (locked == null) {
            throw error(MvpErrorCode.RESOURCE_NOT_FOUND, "Resource not found");
        }

        if (conversationMessageMapper.existsRequestId(
            currentUser.tenantId(), currentUser.userId(), valid.conversationId(), valid.requestId())) {
            throw error(MvpErrorCode.DUPLICATE_REQUEST, "Duplicate request");
        }
        if (conversationMessageMapper.existsActiveAssistant(
            currentUser.tenantId(), currentUser.userId(), valid.conversationId())) {
            throw error(MvpErrorCode.CONVERSATION_BUSY, "Conversation is busy");
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
                throw error(MvpErrorCode.DATABASE_UNAVAILABLE, "Database unavailable");
            }
        } catch (DuplicateKeyException exception) {
            if (isRequestRoleDuplicate(exception)) {
                throw error(MvpErrorCode.DUPLICATE_REQUEST, "Duplicate request", exception);
            }
            throw exception;
        } catch (DataAccessException exception) {
            throw error(MvpErrorCode.DATABASE_UNAVAILABLE, "Database unavailable", exception);
        }

        return new ConversationExecutionResult(
            valid.conversationId(), valid.requestId(), userMessageId, assistantMessageId, turnNo);
    }

    @Override
    @Transactional
    public void markStreaming(CurrentUser currentUser, String assistantMessageId) {
        CurrentUser validUser = validateCurrentUser(currentUser);
        String validAssistantMessageId = validateId(assistantMessageId);
        executeStateUpdate(() -> conversationMessageMapper.markOwnedAssistantStreaming(
            validUser.tenantId(), validUser.userId(), validAssistantMessageId, Instant.now(clock)));
    }

    @Override
    @Transactional
    public void complete(CurrentUser currentUser, MessageCompletionCommand command) {
        CurrentUser validUser = validateCurrentUser(currentUser);
        ValidatedCompletion valid = validateCompletion(command);
        snapshotValidator().validate(valid.snapshotJson(), valid.payloadVersion());
        executeStateUpdate(() -> conversationMessageMapper.completeOwnedStreamingAssistant(
            validUser.tenantId(),
            validUser.userId(),
            valid.assistantMessageId(),
            valid.finalContent(),
            valid.snapshotJson(),
            valid.payloadVersion(),
            Instant.now(clock)
        ));
    }

    @Override
    @Transactional
    public void fail(CurrentUser currentUser, MessageFailureCommand command) {
        finishActiveAssistant(currentUser, command, "FAILED");
    }

    @Override
    @Transactional
    public void interrupt(CurrentUser currentUser, MessageFailureCommand command) {
        finishActiveAssistant(currentUser, command, "INTERRUPTED");
    }

    @Override
    public List<ConversationHistoryItem> loadCompletedHistory(CurrentUser currentUser, String conversationId,
                                                              String excludeRequestId, int maxTurns,
                                                              int maxCharacters) {
        return conversationHistoryService.loadCompletedHistory(
            currentUser, conversationId, excludeRequestId, maxTurns, maxCharacters);
    }

    private void finishActiveAssistant(CurrentUser currentUser, MessageFailureCommand command, String toStatus) {
        CurrentUser validUser = validateCurrentUser(currentUser);
        ValidatedFailure valid = validateFailure(command);
        String validPartialSnapshot = snapshotValidator().validOrNull(valid.partialSnapshotJson(), valid.payloadVersion());
        executeStateUpdate(() -> conversationMessageMapper.finishOwnedActiveAssistant(
            validUser.tenantId(),
            validUser.userId(),
            valid.assistantMessageId(),
            toStatus,
            valid.errorCode(),
            valid.errorMessage(),
            validPartialSnapshot,
            valid.payloadVersion(),
            Instant.now(clock)
        ));
    }

    private void executeStateUpdate(StateUpdate update) {
        int updated;
        try {
            updated = update.execute();
        } catch (DataAccessException exception) {
            throw error(MvpErrorCode.DATABASE_UNAVAILABLE, "Database unavailable", exception);
        }
        if (updated != 1) {
            throw error(MvpErrorCode.MESSAGE_STATE_CONFLICT, "Message state conflict");
        }
    }

    private ValidatedCommand validate(CurrentUser currentUser, ConversationExecutionCommand command) {
        validateCurrentUser(currentUser);
        if (command == null) {
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

    private ValidatedCompletion validateCompletion(MessageCompletionCommand command) {
        if (command == null) {
            throw validationError();
        }
        String assistantMessageId = validateId(command.assistantMessageId());
        if (command.finalContent() == null) {
            throw validationError();
        }
        if (command.snapshotJson() == null || command.snapshotJson().isBlank()) {
            throw validationError();
        }
        return new ValidatedCompletion(
            assistantMessageId,
            command.finalContent(),
            command.snapshotJson(),
            command.payloadVersion()
        );
    }

    private ValidatedFailure validateFailure(MessageFailureCommand command) {
        if (command == null) {
            throw validationError();
        }
        String assistantMessageId = validateId(command.assistantMessageId());
        String errorCode = trim(command.errorCode());
        String errorMessage = trim(command.errorMessage());
        if (isBlank(errorCode) || errorCode.length() > MAX_ERROR_CODE_LENGTH) {
            throw validationError();
        }
        if (errorMessage != null && errorMessage.length() > MAX_ERROR_MESSAGE_LENGTH) {
            throw validationError();
        }
        Integer payloadVersion = command.payloadVersion() == null ? PAYLOAD_VERSION : command.payloadVersion();
        if (payloadVersion != PAYLOAD_VERSION) {
            throw validationError();
        }
        return new ValidatedFailure(
            assistantMessageId,
            errorCode,
            errorMessage,
            command.partialSnapshotJson(),
            payloadVersion
        );
    }

    private CurrentUser validateCurrentUser(CurrentUser currentUser) {
        if (currentUser == null || isBlank(currentUser.tenantId()) || isBlank(currentUser.userId())) {
            throw validationError();
        }
        return currentUser;
    }

    private String validateId(String value) {
        String id = trim(value);
        if (isBlank(id)) {
            throw validationError();
        }
        return id;
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

    private SnapshotValidator snapshotValidator() {
        return new SnapshotValidator(SNAPSHOT_OBJECT_MAPPER, snapshotMaxBytes);
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
        return error(MvpErrorCode.VALIDATION_ERROR, "Invalid request");
    }

    private ConversationException error(MvpErrorCode code, String message) {
        return new ConversationException(code, message);
    }

    private ConversationException error(MvpErrorCode code, String message, Throwable cause) {
        return new ConversationException(code, message, cause);
    }

    private interface StateUpdate {
        int execute();
    }

    private record ValidatedCommand(
        String conversationId,
        String requestId,
        String query,
        Integer deepThink,
        String outputStyle
    ) {
    }

    private record ValidatedCompletion(
        String assistantMessageId,
        String finalContent,
        String snapshotJson,
        int payloadVersion
    ) {
    }

    private record ValidatedFailure(
        String assistantMessageId,
        String errorCode,
        String errorMessage,
        String partialSnapshotJson,
        Integer payloadVersion
    ) {
    }
}
