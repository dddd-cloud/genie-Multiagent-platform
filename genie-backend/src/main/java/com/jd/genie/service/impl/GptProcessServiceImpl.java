package com.jd.genie.service.impl;

import com.jd.genie.model.req.GptQueryReq;
import com.jd.genie.platform.agentbridge.AgentBridgeErrorMapper;
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
import com.jd.genie.platform.phase2.runtime.request.Phase2GptQueryRequest;
import com.jd.genie.platform.phase2.runtime.request.Phase2GptQueryRequestValidator;
import com.jd.genie.platform.phase2.runtime.request.Phase2GptQueryRequestValidator.ValidatedPhase2Request;
import com.jd.genie.platform.phase2.runtime.orchestration.Phase2OrchestrationRuntime;
import com.jd.genie.platform.phase2.runtime.route.RouteDecision;
import com.jd.genie.platform.phase2contract.dto.AgentCapabilitySummary;
import com.jd.genie.platform.phase2contract.error.Phase2ContractException;
import com.jd.genie.platform.phase2contract.port.AgentRuntimeCatalogPort;
import com.jd.genie.agent.util.ThreadUtil;
import com.jd.genie.service.IGptProcessService;
import com.jd.genie.service.IMultiAgentService;
import com.jd.genie.model.response.GptProcessResult;
import com.jd.genie.util.SseUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;

@Slf4j
@Service
public class GptProcessServiceImpl implements IGptProcessService {
    private final IMultiAgentService multiAgentService;
    private final CurrentUserProvider currentUserProvider;
    private final ConversationExecutionPort executionPort;
    private final AgentExecutionRequestFactory requestFactory;
    private final AgentHistoryMessageMapper historyMessageMapper;
    private final ObjectProvider<AgentRuntimeCatalogPort> agentRuntimeCatalogPortProvider;
    private final ObjectProvider<Phase2OrchestrationRuntime> orchestrationRuntimeProvider;
    private final Phase2GptQueryRequestValidator phase2RequestValidator;
    private final long sseTimeoutMillis;
    private final long maxSnapshotBytes;
    private final int historyMaxTurns;
    private final int historyMaxCharacters;
    private final ConversationStreamCoordinator conversationStreamCoordinator;

    public GptProcessServiceImpl(
            IMultiAgentService multiAgentService,
            CurrentUserProvider currentUserProvider,
            ConversationExecutionPort executionPort,
            @Value("${GENIE_SSE_TIMEOUT_MILLIS:3600000}") long sseTimeoutMillis,
            @Value("${GENIE_STREAM_SNAPSHOT_MAX_BYTES:8388608}") long maxSnapshotBytes,
            @Value("${GENIE_HISTORY_MAX_TURNS:6}") int historyMaxTurns,
            @Value("${GENIE_HISTORY_MAX_CHARACTERS:12000}") int historyMaxCharacters
    ) {
        this(
                multiAgentService,
                currentUserProvider,
                executionPort,
                new StaticListableBeanFactory().getBeanProvider(AgentRuntimeCatalogPort.class),
                new StaticListableBeanFactory().getBeanProvider(Phase2OrchestrationRuntime.class),
                sseTimeoutMillis,
                maxSnapshotBytes,
                historyMaxTurns,
                historyMaxCharacters
        );
    }

    public GptProcessServiceImpl(
            IMultiAgentService multiAgentService,
            CurrentUserProvider currentUserProvider,
            ConversationExecutionPort executionPort,
            ObjectProvider<AgentRuntimeCatalogPort> agentRuntimeCatalogPortProvider,
            @Value("${GENIE_SSE_TIMEOUT_MILLIS:3600000}") long sseTimeoutMillis,
            @Value("${GENIE_STREAM_SNAPSHOT_MAX_BYTES:8388608}") long maxSnapshotBytes,
            @Value("${GENIE_HISTORY_MAX_TURNS:6}") int historyMaxTurns,
            @Value("${GENIE_HISTORY_MAX_CHARACTERS:12000}") int historyMaxCharacters
    ) {
        this(
                multiAgentService,
                currentUserProvider,
                executionPort,
                agentRuntimeCatalogPortProvider,
                new StaticListableBeanFactory().getBeanProvider(Phase2OrchestrationRuntime.class),
                sseTimeoutMillis,
                maxSnapshotBytes,
                historyMaxTurns,
                historyMaxCharacters
        );
    }

    @Autowired
    public GptProcessServiceImpl(
            IMultiAgentService multiAgentService,
            CurrentUserProvider currentUserProvider,
            ConversationExecutionPort executionPort,
            ObjectProvider<AgentRuntimeCatalogPort> agentRuntimeCatalogPortProvider,
            ObjectProvider<Phase2OrchestrationRuntime> orchestrationRuntimeProvider,
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
        this.agentRuntimeCatalogPortProvider = agentRuntimeCatalogPortProvider;
        this.orchestrationRuntimeProvider = orchestrationRuntimeProvider;
        this.phase2RequestValidator = new Phase2GptQueryRequestValidator(requestFactory);
        this.sseTimeoutMillis = requirePositive(sseTimeoutMillis, "sseTimeoutMillis");
        this.maxSnapshotBytes = requirePositive(maxSnapshotBytes, "maxSnapshotBytes");
        this.historyMaxTurns = requirePositive(historyMaxTurns, "historyMaxTurns");
        this.historyMaxCharacters = requirePositive(historyMaxCharacters, "historyMaxCharacters");
        this.conversationStreamCoordinator = new ConversationStreamCoordinator();
    }

    @Override
    public SseEmitter queryMultiAgentIncrStream(GptQueryReq externalRequest) {
        CurrentUser currentUser = currentUserProvider.requireCurrentUser();
        return executeTrustedRequest(currentUser, requestFactory.trustedRequest(externalRequest, currentUser));
    }

    @Override
    public SseEmitter queryPhase2AgentStreamIncr(Phase2GptQueryRequest externalRequest) {
        CurrentUser currentUser = currentUserProvider.requireCurrentUser();
        ValidatedPhase2Request request = phase2RequestValidator.validate(externalRequest, currentUser);
        List<AgentCapabilitySummary> candidates = loadCandidateSnapshot(currentUser, request);
        if ("DIRECT".equals(request.executionMode())) {
            return executeTrustedRequest(currentUser, withDirectRuntimeContext(request));
        }
        if ("AUTO".equals(request.executionMode()) && candidates.isEmpty()) {
            return executeTrustedRequest(currentUser, withDirectRuntimeContext(request));
        }
        if ("ORCHESTRATED".equals(request.executionMode()) && candidates.isEmpty()) {
            throw new AgentBridgeException(MvpErrorCode.NO_SUITABLE_AGENT, "No suitable online Agent is available");
        }
        return executeOrchestrationRequest(currentUser, request, candidates);
    }

    private SseEmitter executeTrustedRequest(CurrentUser currentUser, GptQueryReq request) {
        return executePreparedRequest(currentUser, request, (observer, cancellableCall) -> {
            startAgent(request, observer, cancellableCall);
            log.info(
                    "Agent execution started, conversationId: {}, requestId: {}, traceId: {}, status: STREAMING",
                    request.getSessionId(),
                    request.getRequestId(),
                    request.getTraceId()
            );
        });
    }

    private SseEmitter executeOrchestrationRequest(
            CurrentUser currentUser,
            ValidatedPhase2Request request,
            List<AgentCapabilitySummary> candidates
    ) {
        GptQueryReq trustedRequest = request.trustedRequest();
        return executePreparedRequest(currentUser, trustedRequest, (observer, cancellableCall) -> {
            Phase2OrchestrationRuntime runtime = orchestrationRuntimeProvider.getIfAvailable();
            if (runtime == null) {
                throw new AgentBridgeException(
                        MvpErrorCode.INTERNAL_ERROR,
                        "Phase2 orchestration runtime is not available"
                );
            }
            RouteDecision route = runtime.selectRoute(
                    request.executionMode(),
                    trustedRequest.getQuery(),
                    request.localContext().conversationSummary(),
                    candidates
            );
            if (route.route() == RouteDecision.Route.DIRECT) {
                startAgent(withDirectRuntimeContext(request), observer, cancellableCall);
                return;
            }
            // Return SseEmitter first; sync execute() would buffer all SSE until the request thread finishes.
            ThreadUtil.execute(() -> {
                try {
                    runtime.execute(
                            currentUser,
                            trustedRequest.getRequestId(),
                            UUID.randomUUID().toString(),
                            trustedRequest.getQuery(),
                            request.localContext().conversationSummary(),
                            candidates,
                            route,
                            observer
                    );
                } catch (Throwable error) {
                    if (!observer.isTerminal()) {
                        observer.onError(error);
                    }
                }
            });
        });
    }

    private SseEmitter executePreparedRequest(
            CurrentUser currentUser,
            GptQueryReq request,
            PreparedExecutionStarter starter
    ) {
        return conversationStreamCoordinator.execute(currentUser, request, starter);
    }

    private GptQueryReq withDirectRuntimeContext(ValidatedPhase2Request request) {
        GptQueryReq trustedRequest = request.trustedRequest();
        String localContext = "[UNTRUSTED_LOCAL_CONTEXT]\nlongTermMemory:\n"
                + request.localContext().longTermMemory()
                + "\nconversationSummary:\n"
                + request.localContext().conversationSummary()
                + "\n[/UNTRUSTED_LOCAL_CONTEXT]";
        trustedRequest.setRuntimeBasePrompt(appendRuntimeContext(
                trustedRequest.getRuntimeBasePrompt(),
                localContext
        ));
        trustedRequest.setRuntimeSopPrompt(appendRuntimeContext(
                trustedRequest.getRuntimeSopPrompt(),
                localContext
        ));
        return trustedRequest;
    }

    private String appendRuntimeContext(String existingPrompt, String localContext) {
        return hasText(existingPrompt) ? existingPrompt + "\n\n" + localContext : localContext;
    }

    private List<AgentCapabilitySummary> loadCandidateSnapshot(
            CurrentUser currentUser,
            ValidatedPhase2Request request
    ) {
        if ("DIRECT".equals(request.executionMode())) {
            return List.of();
        }
        AgentRuntimeCatalogPort catalogPort = agentRuntimeCatalogPortProvider.getIfAvailable();
        if (catalogPort == null) {
            throw new AgentBridgeException(
                    MvpErrorCode.INTERNAL_ERROR,
                    "Agent runtime catalog is not available"
            );
        }
        try {
            List<AgentCapabilitySummary> candidates = catalogPort.listOnlineCandidates(
                    currentUser,
                    request.allowedAgentIds()
            );
            if (candidates == null) {
                throw new AgentBridgeException(MvpErrorCode.INTERNAL_ERROR, "Agent runtime catalog returned no snapshot");
            }
            return List.copyOf(candidates);
        } catch (Phase2ContractException error) {
            throw new AgentBridgeException(error.errorCode(), error.getMessage(), error);
        } catch (RuntimeException error) {
            throw AgentBridgeErrorMapper.asAgentBridgeException(error, MvpErrorCode.INTERNAL_ERROR);
        }
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

    private void registerLifecycle(
            SseEmitter emitter,
            String traceId,
            StreamPersistenceObserver persistence,
            ConversationStreamObserver observer,
            ScheduledFuture<?> heartbeatFuture
    ) {
        try {
            SseUtil.registerLifecycle(
                    emitter,
                    traceId,
                    ignored -> observer.onClientDisconnected(),
                    heartbeatFuture
            );
        } catch (RuntimeException error) {
            failPreparedExecution(persistence, error);
            throw error;
        }
    }

    private static GptProcessResult buildConversationHeartbeat(String traceId) {
        GptProcessResult heartbeat = new GptProcessResult();
        heartbeat.setFinished(false);
        heartbeat.setStatus("success");
        heartbeat.setResponseType("text");
        heartbeat.setResponse("");
        heartbeat.setResponseAll("");
        heartbeat.setTraceId(traceId);
        heartbeat.setReqId(traceId);
        heartbeat.setPackageType("heartbeat");
        heartbeat.setEncrypted(false);
        return heartbeat;
    }

    private void startAgent(
            GptQueryReq request,
            ConversationStreamObserver observer,
            CancellableAgentCall cancellableCall
    ) {
        multiAgentService.searchForAgentRequest(request, observer, cancellableCall);
    }

    /**
     * V1 与 V2 仅在执行器选择上不同；会话状态机必须经过同一协调边界。
     */
    private final class ConversationStreamCoordinator {
        private SseEmitter execute(
                CurrentUser currentUser,
                GptQueryReq request,
                PreparedExecutionStarter starter
        ) {
            ConversationExecutionResult execution;
            try {
                execution = prepareExecution(currentUser, request);
            } catch (RuntimeException error) {
                throw AgentBridgeErrorMapper.asAgentBridgeException(error, MvpErrorCode.INTERNAL_ERROR);
            }
            StreamPersistenceObserver persistence = new StreamPersistenceObserver(
                    executionPort,
                    currentUser,
                    execution.assistantMessageId()
            );
            try {
                loadHistory(currentUser, request, persistence);
                SseEmitter emitter = createEmitter(persistence);
                CancellableAgentCall cancellableCall = new CancellableAgentCall();
                SseEmitterClientChannel clientChannel =
                        new SseEmitterClientChannel(emitter, request.getTraceId());
                ConversationStreamObserver observer = new ConversationStreamObserver(
                        persistence,
                        clientChannel,
                        maxSnapshotBytes,
                        cancellableCall
                );
                ScheduledFuture<?> heartbeatFuture = SseUtil.startHeartbeat(
                        request.getTraceId(),
                        () -> observer.onEventBestEffort(buildConversationHeartbeat(request.getTraceId()))
                );
                registerLifecycle(emitter, request.getTraceId(), persistence, observer, heartbeatFuture);
                try {
                    if (!observer.markStreaming()) {
                        heartbeatFuture.cancel(false);
                        return emitter;
                    }
                    starter.start(observer, cancellableCall);
                    return emitter;
                } catch (RuntimeException error) {
                    heartbeatFuture.cancel(false);
                    failPreparedExecution(persistence, error);
                    throw error;
                }
            } catch (RuntimeException error) {
                throw AgentBridgeErrorMapper.asAgentBridgeException(error, MvpErrorCode.INTERNAL_ERROR);
            }
        }
    }

    @FunctionalInterface
    private interface PreparedExecutionStarter {
        void start(ConversationStreamObserver observer, CancellableAgentCall cancellableCall);
    }

    private void failPreparedExecution(
            StreamPersistenceObserver persistence,
            RuntimeException originalError
    ) {
        MvpErrorCode errorCode = errorCodeOf(originalError);
        try {
            persistence.fail(
                    errorCode,
                    safeMessage(originalError, errorCode),
                    null,
                    null
            );
        } catch (RuntimeException persistenceError) {
            originalError.addSuppressed(persistenceError);
        }
    }

    private MvpErrorCode errorCodeOf(RuntimeException error) {
        return AgentBridgeErrorMapper.errorCode(error, MvpErrorCode.INTERNAL_ERROR);
    }

    private String safeMessage(RuntimeException error, MvpErrorCode errorCode) {
        return AgentBridgeErrorMapper.message(error, errorCode);
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
