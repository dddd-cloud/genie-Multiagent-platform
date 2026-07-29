package com.jd.genie.service.impl;

import com.jd.genie.model.req.GptQueryReq;
import com.jd.genie.platform.agentbridge.AgentBridgeException;
import com.jd.genie.platform.agentbridge.AgentExecutionRequestFactory;
import com.jd.genie.platform.agentbridge.AgentHistoryMessageMapper;
import com.jd.genie.platform.agentbridge.CancellableAgentCall;
import com.jd.genie.platform.agentbridge.ConversationStreamObserver;
import com.jd.genie.platform.agentbridge.SseEmitterClientChannel;
import com.jd.genie.platform.agentbridge.StreamPersistenceObserver;
import com.jd.genie.platform.contract.ConversationExecutionCommand;
import com.jd.genie.platform.contract.ConversationExecutionPort;
import com.jd.genie.platform.contract.ConversationExecutionResult;
import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.CurrentUserProvider;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.service.IGptProcessService;
import com.jd.genie.service.IMultiAgentService;
import com.jd.genie.util.SseUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@Service
public class GptProcessServiceImpl implements IGptProcessService {
    private static final int MAX_ERROR_MESSAGE_LENGTH = 1_000;

    private final IMultiAgentService multiAgentService;
    private final CurrentUserProvider currentUserProvider;
    private final ConversationExecutionPort executionPort;
    private final AgentExecutionRequestFactory requestFactory;
    private final AgentHistoryMessageMapper historyMessageMapper;
    private final long sseTimeoutMillis;
    private final long maxSnapshotBytes;
    private final int historyMaxTurns;
    private final int historyMaxCharacters;

    public GptProcessServiceImpl(
            IMultiAgentService multiAgentService,
            CurrentUserProvider currentUserProvider,
            ConversationExecutionPort executionPort,
            @Value("${GENIE_SSE_TIMEOUT_MILLIS:3600000}") long sseTimeoutMillis,
            @Value("${GENIE_STREAM_SNAPSHOT_MAX_BYTES:8388608}") long maxSnapshotBytes,
            @Value("${GENIE_HISTORY_MAX_TURNS:6}") int historyMaxTurns,
            @Value("${GENIE_HISTORY_MAX_CHARACTERS:12000}") int historyMaxCharacters
    ) {
        this.multiAgentService = multiAgentService;
        this.currentUserProvider = currentUserProvider;
        this.executionPort = executionPort;
        this.requestFactory = new AgentExecutionRequestFactory();
        this.historyMessageMapper = new AgentHistoryMessageMapper();
        this.sseTimeoutMillis = requirePositive(sseTimeoutMillis, "sseTimeoutMillis");
        this.maxSnapshotBytes = requirePositive(maxSnapshotBytes, "maxSnapshotBytes");
        this.historyMaxTurns = requirePositive(historyMaxTurns, "historyMaxTurns");
        this.historyMaxCharacters = requirePositive(historyMaxCharacters, "historyMaxCharacters");
    }

    @Override
    public SseEmitter queryMultiAgentIncrStream(GptQueryReq externalRequest) {
        CurrentUser currentUser = currentUserProvider.requireCurrentUser();
        GptQueryReq request = requestFactory.trustedRequest(externalRequest, currentUser);
        ConversationExecutionResult execution = prepareExecution(currentUser, request);
        StreamPersistenceObserver persistence = new StreamPersistenceObserver(
                executionPort,
                currentUser,
                execution.assistantMessageId()
        );

        loadHistory(currentUser, request, persistence);
        SseEmitter emitter = createEmitter(persistence);
        CancellableAgentCall cancellableCall = new CancellableAgentCall();
        ConversationStreamObserver observer = new ConversationStreamObserver(
                persistence,
                new SseEmitterClientChannel(emitter, request.getTraceId()),
                maxSnapshotBytes,
                cancellableCall
        );
        if (!registerLifecycle(emitter, request.getTraceId(), observer)) {
            return emitter;
        }
        if (!observer.markStreaming()) {
            return emitter;
        }
        if (!startAgent(request, observer, cancellableCall)) {
            return emitter;
        }

        log.info(
                "Agent execution started, conversationId: {}, requestId: {}, traceId: {}, status: STREAMING",
                request.getSessionId(),
                request.getRequestId(),
                request.getTraceId()
        );
        return emitter;
    }

    private ConversationExecutionResult prepareExecution(
            CurrentUser currentUser,
            GptQueryReq request
    ) {
        ConversationExecutionResult execution = executionPort.prepareExecution(
                currentUser,
                new ConversationExecutionCommand(
                        request.getSessionId(),
                        request.getRequestId(),
                        request.getQuery(),
                        request.getDeepThink(),
                        request.getOutputStyle()
                )
        );
        if (execution == null || !hasText(execution.assistantMessageId())) {
            throw internalError("prepareExecution returned no assistant message", null);
        }
        return execution;
    }

    private void loadHistory(
            CurrentUser currentUser,
            GptQueryReq request,
            StreamPersistenceObserver persistence
    ) {
        try {
            request.setHistoryMessages(historyMessageMapper.toAgentRequestMessages(
                    executionPort.loadCompletedHistory(
                            currentUser,
                            request.getSessionId(),
                            request.getRequestId(),
                            historyMaxTurns,
                            historyMaxCharacters
                    )
            ));
        } catch (RuntimeException error) {
            failPreparedExecution(persistence, error);
            throw error;
        }
    }

    private SseEmitter createEmitter(StreamPersistenceObserver persistence) {
        try {
            return SseUtil.create(sseTimeoutMillis);
        } catch (RuntimeException error) {
            failPreparedExecution(persistence, error);
            throw error;
        }
    }

    private boolean registerLifecycle(
            SseEmitter emitter,
            String traceId,
            ConversationStreamObserver observer
    ) {
        try {
            SseUtil.registerLifecycle(
                    emitter,
                    traceId,
                    ignored -> observer.onClientDisconnected()
            );
            return true;
        } catch (RuntimeException error) {
            observer.onError(internalError("Failed to register SSE lifecycle", error));
            return false;
        }
    }

    private boolean startAgent(
            GptQueryReq request,
            ConversationStreamObserver observer,
            CancellableAgentCall cancellableCall
    ) {
        try {
            multiAgentService.searchForAgentRequest(request, observer, cancellableCall);
            return true;
        } catch (RuntimeException error) {
            observer.onError(error);
            return false;
        }
    }

    private void failPreparedExecution(
            StreamPersistenceObserver persistence,
            RuntimeException originalError
    ) {
        try {
            persistence.fail(
                    errorCodeOf(originalError),
                    safeMessage(originalError),
                    null,
                    null
            );
        } catch (RuntimeException persistenceError) {
            originalError.addSuppressed(persistenceError);
        }
    }

    private MvpErrorCode errorCodeOf(RuntimeException error) {
        return error instanceof AgentBridgeException bridgeException
                ? bridgeException.getErrorCode()
                : MvpErrorCode.INTERNAL_ERROR;
    }

    private String safeMessage(RuntimeException error) {
        String message = error.getMessage();
        String safe = hasText(message) ? message : MvpErrorCode.INTERNAL_ERROR.name();
        return safe.length() <= MAX_ERROR_MESSAGE_LENGTH
                ? safe
                : safe.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }

    private AgentBridgeException internalError(String message, Throwable cause) {
        return cause == null
                ? new AgentBridgeException(MvpErrorCode.INTERNAL_ERROR, message)
                : new AgentBridgeException(MvpErrorCode.INTERNAL_ERROR, message, cause);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static int requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private static long requirePositive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}
