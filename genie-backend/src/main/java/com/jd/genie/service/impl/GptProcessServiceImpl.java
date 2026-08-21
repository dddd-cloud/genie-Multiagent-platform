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
import com.jd.genie.platform.conversation.attachment.ChatAttachmentPrompt;
import com.jd.genie.platform.conversation.service.ConversationAttachmentService;
import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.CurrentUserProvider;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2.configuration.model.ModelCatalogService;
import com.jd.genie.platform.phase2.runtime.request.Phase2GptQueryRequest;
import com.jd.genie.platform.phase2.runtime.request.Phase2GptQueryRequestValidator;
import com.jd.genie.platform.phase2.runtime.request.Phase2GptQueryRequestValidator.LocalContextSnapshot;
import com.jd.genie.platform.phase2.runtime.request.Phase2GptQueryRequestValidator.ValidatedPhase2Request;
import com.jd.genie.platform.phase2.memory.store.LocalMemorySnapshot;
import com.jd.genie.platform.phase2.memory.store.MemoryDocumentService;
import com.jd.genie.platform.phase2.runtime.orchestration.OrchestrationConversationHistory;
import com.jd.genie.platform.phase2.runtime.orchestration.Phase2OrchestrationRuntime;
import com.jd.genie.platform.phase2.runtime.resource.SystemResourceBuilder;
import com.jd.genie.platform.phase2.runtime.route.DispatchDecision;
import com.jd.genie.platform.phase2.runtime.route.RouteDecision;
import com.jd.genie.platform.phase2contract.dto.AgentCapabilitySummary;
import com.jd.genie.platform.phase2contract.dto.MasterPersona;
import com.jd.genie.platform.phase2contract.dto.TeamCapabilitySummary;
import com.jd.genie.platform.phase2contract.dto.TeamRuntimeSelection;
import com.jd.genie.platform.phase2contract.error.Phase2ContractException;
import com.jd.genie.platform.phase2contract.port.AgentRuntimeCatalogPort;
import com.jd.genie.platform.phase2contract.port.TeamRuntimeCatalogPort;
import com.jd.genie.agent.llm.RequestScopedLlmSettings;
import com.jd.genie.agent.llm.RequestTokenUsage;
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
    private final ObjectProvider<MemoryDocumentService> memoryDocumentServiceProvider;
    private final ObjectProvider<TeamRuntimeCatalogPort> teamRuntimeCatalogPortProvider;
    private final Phase2GptQueryRequestValidator phase2RequestValidator;
    private final long sseTimeoutMillis;
    private final long maxSnapshotBytes;
    private final int historyMaxTurns;
    private final int historyMaxCharacters;
    private final ConversationStreamCoordinator conversationStreamCoordinator;
    private ObjectProvider<ConversationAttachmentService> attachmentServiceProvider =
            new StaticListableBeanFactory().getBeanProvider(ConversationAttachmentService.class);
    private ObjectProvider<ModelCatalogService> modelCatalogServiceProvider =
            new StaticListableBeanFactory().getBeanProvider(ModelCatalogService.class);

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
                new StaticListableBeanFactory().getBeanProvider(MemoryDocumentService.class),
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
            ObjectProvider<Phase2OrchestrationRuntime> orchestrationRuntimeProvider,
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
                orchestrationRuntimeProvider,
                new StaticListableBeanFactory().getBeanProvider(MemoryDocumentService.class),
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
            ObjectProvider<Phase2OrchestrationRuntime> orchestrationRuntimeProvider,
            ObjectProvider<MemoryDocumentService> memoryDocumentServiceProvider,
            long sseTimeoutMillis,
            long maxSnapshotBytes,
            int historyMaxTurns,
            int historyMaxCharacters
    ) {
        this(
                multiAgentService,
                currentUserProvider,
                executionPort,
                agentRuntimeCatalogPortProvider,
                orchestrationRuntimeProvider,
                memoryDocumentServiceProvider,
                new StaticListableBeanFactory().getBeanProvider(TeamRuntimeCatalogPort.class),
                new StaticListableBeanFactory().getBeanProvider(ConversationAttachmentService.class),
                new StaticListableBeanFactory().getBeanProvider(ModelCatalogService.class),
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
            ObjectProvider<MemoryDocumentService> memoryDocumentServiceProvider,
            ObjectProvider<TeamRuntimeCatalogPort> teamRuntimeCatalogPortProvider,
            ObjectProvider<ConversationAttachmentService> attachmentServiceProvider,
            ObjectProvider<ModelCatalogService> modelCatalogServiceProvider,
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
        this.memoryDocumentServiceProvider = memoryDocumentServiceProvider;
        this.teamRuntimeCatalogPortProvider = teamRuntimeCatalogPortProvider;
        this.attachmentServiceProvider = attachmentServiceProvider == null
                ? new StaticListableBeanFactory().getBeanProvider(ConversationAttachmentService.class)
                : attachmentServiceProvider;
        this.modelCatalogServiceProvider = modelCatalogServiceProvider == null
                ? new StaticListableBeanFactory().getBeanProvider(ModelCatalogService.class)
                : modelCatalogServiceProvider;
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
        OrchestrationTargets targets = loadOrchestrationTargets(currentUser, request);
        if ("DIRECT".equals(request.executionMode())) {
            if (targets.candidates().size() != 1) {
                throw new AgentBridgeException(MvpErrorCode.NO_SUITABLE_AGENT, "No suitable online Agent is available");
            }
            return executeSoloRequest(currentUser, request, targets.candidates().get(0),
                    new RouteDecision(RouteDecision.Route.ORCHESTRATED, "SOLO_AGENT"));
        }
        List<TeamCapabilitySummary> teams = "AUTO".equals(request.executionMode())
                ? listAvailableTeams(currentUser)
                : List.of();
        if ("AUTO".equals(request.executionMode())
                && targets.candidates().isEmpty()
                && teams.isEmpty()) {
            throw new AgentBridgeException(MvpErrorCode.NO_SUITABLE_AGENT, "No suitable online Agent is available");
        }
        if ("ORCHESTRATED".equals(request.executionMode()) && targets.candidates().isEmpty()) {
            throw new AgentBridgeException(MvpErrorCode.NO_SUITABLE_AGENT, "No suitable online Agent is available");
        }
        if ("AUTO".equals(request.executionMode())
                && !SystemResourceBuilder.requiresResourceCreation(request.trustedRequest().getQuery())) {
            return executeAutoRequest(currentUser, request, targets.candidates(), teams);
        }
        return executeOrchestrationRequest(currentUser, request, targets);
    }

    private SseEmitter executeTrustedRequest(CurrentUser currentUser, GptQueryReq request) {
        return executePreparedRequest(currentUser, request, (observer, cancellableCall) -> {
            applySelectedModel(currentUser, request);
            applyChatAttachments(currentUser, request);
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
            OrchestrationTargets targets
    ) {
        List<AgentCapabilitySummary> candidates = targets.candidates();
        MasterPersona masterPersona = targets.masterPersona();
        GptQueryReq trustedRequest = withDirectRuntimeContext(currentUser, request);
        return executePreparedRequest(currentUser, trustedRequest, (observer, cancellableCall) -> {
            applySelectedModel(currentUser, trustedRequest);
            ChatAttachmentPrompt.Prompts attachmentPrompts = attachmentPrompts(currentUser, trustedRequest);
            Phase2OrchestrationRuntime runtime = orchestrationRuntimeProvider.getIfAvailable();
            if (runtime == null) {
                throw new AgentBridgeException(
                        MvpErrorCode.INTERNAL_ERROR,
                        "Phase2 orchestration runtime is not available"
                );
            }
            String conversationHistory = OrchestrationConversationHistory.format(trustedRequest.getHistoryMessages());
            LocalContextSnapshot localContext = effectiveLocalContext(currentUser, request);
            RouteDecision route = runtime.selectRoute(
                    request.executionMode(),
                    attachmentPrompts.routingQuery(),
                    localContext.conversationSummary(),
                    conversationHistory,
                    candidates,
                    masterPersona
            );
            if (route.route() == RouteDecision.Route.DIRECT) {
                if (candidates.isEmpty()) {
                    throw new AgentBridgeException(MvpErrorCode.NO_SUITABLE_AGENT, "No suitable online Agent is available");
                }
                ThreadUtil.execute(() -> {
                    try {
                        applySelectedModel(currentUser, trustedRequest);
                        runtime.executeSolo(
                                currentUser,
                                trustedRequest.getRequestId(),
                                UUID.randomUUID().toString(),
                                attachmentPrompts.routingQuery(),
                                localContext.conversationSummary(),
                                localContext.longTermMemory(),
                                conversationHistory,
                                candidates.get(0),
                                new RouteDecision(RouteDecision.Route.ORCHESTRATED, route.reasonCode()),
                                observer,
                                attachmentPrompts.specialistQuery(),
                                trustedRequest.getSessionId()
                        );
                    } catch (Throwable error) {
                        if (!observer.isTerminal()) {
                            observer.onError(error);
                        }
                    } finally {
                        RequestScopedLlmSettings.clear();
                        RequestTokenUsage.clearBillingRequestId();
                    }
                });
                return;
            }
            // Return SseEmitter first; sync execute() would buffer all SSE until the request thread finishes.
            ThreadUtil.execute(() -> {
                try {
                    applySelectedModel(currentUser, trustedRequest);
                    runtime.execute(
                            currentUser,
                            trustedRequest.getRequestId(),
                            UUID.randomUUID().toString(),
                            attachmentPrompts.routingQuery(),
                            localContext.conversationSummary(),
                            localContext.longTermMemory(),
                            conversationHistory,
                            candidates,
                            route,
                            observer,
                            masterPersona,
                            request.teamId(),
                            attachmentPrompts.specialistQuery(),
                            trustedRequest.getSessionId()
                    );
                } catch (Throwable error) {
                    if (!observer.isTerminal()) {
                        observer.onError(error);
                    }
                } finally {
                    RequestScopedLlmSettings.clear();
                    RequestTokenUsage.clearBillingRequestId();
                }
            });
        });
    }

    private SseEmitter executeSoloRequest(
            CurrentUser currentUser,
            ValidatedPhase2Request request,
            AgentCapabilitySummary agent,
            RouteDecision route
    ) {
        GptQueryReq trustedRequest = withDirectRuntimeContext(currentUser, request);
        return executePreparedRequest(currentUser, trustedRequest, (observer, cancellableCall) -> {
            applySelectedModel(currentUser, trustedRequest);
            ChatAttachmentPrompt.Prompts attachmentPrompts = attachmentPrompts(currentUser, trustedRequest);
            Phase2OrchestrationRuntime runtime = requireOrchestrationRuntime();
            String conversationHistory = OrchestrationConversationHistory.format(trustedRequest.getHistoryMessages());
            LocalContextSnapshot localContext = effectiveLocalContext(currentUser, request);
            ThreadUtil.execute(() -> {
                try {
                    applySelectedModel(currentUser, trustedRequest);
                    runtime.executeSolo(
                            currentUser,
                            trustedRequest.getRequestId(),
                            UUID.randomUUID().toString(),
                            attachmentPrompts.routingQuery(),
                            localContext.conversationSummary(),
                            localContext.longTermMemory(),
                            conversationHistory,
                            agent,
                            route,
                            observer,
                            attachmentPrompts.specialistQuery(),
                            trustedRequest.getSessionId()
                    );
                } catch (Throwable error) {
                    if (!observer.isTerminal()) {
                        observer.onError(error);
                    }
                } finally {
                    RequestScopedLlmSettings.clear();
                    RequestTokenUsage.clearBillingRequestId();
                }
            });
        });
    }

    private SseEmitter executeAutoRequest(
            CurrentUser currentUser,
            ValidatedPhase2Request request,
            List<AgentCapabilitySummary> agents,
            List<TeamCapabilitySummary> teams
    ) {
        GptQueryReq trustedRequest = withDirectRuntimeContext(currentUser, request);
        return executePreparedRequest(currentUser, trustedRequest, (observer, cancellableCall) -> {
            applySelectedModel(currentUser, trustedRequest);
            ChatAttachmentPrompt.Prompts attachmentPrompts = attachmentPrompts(currentUser, trustedRequest);
            Phase2OrchestrationRuntime runtime = requireOrchestrationRuntime();
            String conversationHistory = OrchestrationConversationHistory.format(trustedRequest.getHistoryMessages());
            LocalContextSnapshot localContext = effectiveLocalContext(currentUser, request);
            ThreadUtil.execute(() -> {
                try {
                    applySelectedModel(currentUser, trustedRequest);
                    DispatchDecision decision = runtime.selectDispatch(
                            attachmentPrompts.routingQuery(),
                            localContext.conversationSummary(),
                            conversationHistory,
                            agents,
                            teams
                    );
                    if (decision.kind() == DispatchDecision.Kind.AGENT) {
                        AgentCapabilitySummary agent = agents.stream()
                                .filter(item -> decision.targetId().equals(item.agentId()))
                                .findFirst()
                                .orElseThrow(() -> new AgentBridgeException(
                                        MvpErrorCode.NO_SUITABLE_AGENT, "No suitable online Agent is available"));
                        runtime.executeSolo(
                                currentUser,
                                trustedRequest.getRequestId(),
                                UUID.randomUUID().toString(),
                                attachmentPrompts.routingQuery(),
                                localContext.conversationSummary(),
                                localContext.longTermMemory(),
                                conversationHistory,
                                agent,
                                new RouteDecision(RouteDecision.Route.ORCHESTRATED, "AUTO_SINGLE_AGENT"),
                                observer,
                                attachmentPrompts.specialistQuery(),
                                trustedRequest.getSessionId()
                        );
                        return;
                    }
                    OrchestrationTargets teamTargets = withSystemResourceBuilder(
                            loadTeamTargets(currentUser, decision.targetId()),
                            trustedRequest.getQuery()
                    );
                    runtime.execute(
                            currentUser,
                            trustedRequest.getRequestId(),
                            UUID.randomUUID().toString(),
                            attachmentPrompts.routingQuery(),
                            localContext.conversationSummary(),
                            localContext.longTermMemory(),
                            conversationHistory,
                            teamTargets.candidates(),
                            new RouteDecision(RouteDecision.Route.ORCHESTRATED, "AUTO_TEAM"),
                            observer,
                            teamTargets.masterPersona(),
                            decision.targetId(),
                            attachmentPrompts.specialistQuery(),
                            trustedRequest.getSessionId()
                    );
                } catch (Throwable error) {
                    if (!observer.isTerminal()) {
                        observer.onError(error);
                    }
                } finally {
                    RequestScopedLlmSettings.clear();
                    RequestTokenUsage.clearBillingRequestId();
                }
            });
        });
    }

    private Phase2OrchestrationRuntime requireOrchestrationRuntime() {
        Phase2OrchestrationRuntime runtime = orchestrationRuntimeProvider.getIfAvailable();
        if (runtime == null) {
            throw new AgentBridgeException(
                    MvpErrorCode.INTERNAL_ERROR,
                    "Phase2 orchestration runtime is not available"
            );
        }
        return runtime;
    }

    private SseEmitter executePreparedRequest(
            CurrentUser currentUser,
            GptQueryReq request,
            PreparedExecutionStarter starter
    ) {
        return conversationStreamCoordinator.execute(currentUser, request, starter);
    }

    private GptQueryReq withDirectRuntimeContext(CurrentUser currentUser, ValidatedPhase2Request request) {
        GptQueryReq trustedRequest = request.trustedRequest();
        LocalContextSnapshot localContext = effectiveLocalContext(currentUser, request);
        String encoded = "[UNTRUSTED_LOCAL_CONTEXT]\nlongTermMemory:\n"
                + localContext.longTermMemory()
                + "\nconversationSummary:\n"
                + localContext.conversationSummary()
                + "\n[/UNTRUSTED_LOCAL_CONTEXT]";
        trustedRequest.setRuntimeBasePrompt(appendRuntimeContext(
                trustedRequest.getRuntimeBasePrompt(),
                encoded
        ));
        trustedRequest.setRuntimeSopPrompt(appendRuntimeContext(
                trustedRequest.getRuntimeSopPrompt(),
                encoded
        ));
        return trustedRequest;
    }

    private LocalContextSnapshot effectiveLocalContext(CurrentUser currentUser, ValidatedPhase2Request request) {
        MemoryDocumentService documents = memoryDocumentServiceProvider.getIfAvailable();
        if (documents == null) {
            return request.localContext();
        }
        try {
            LocalMemorySnapshot snapshot = documents.loadForQuery(
                    currentUser.tenantId(),
                    currentUser.userId(),
                    request.trustedRequest().getSessionId()
            );
            return new LocalContextSnapshot(
                    snapshot.longTermMemory() == null ? "" : snapshot.longTermMemory(),
                    snapshot.conversationSummary() == null ? "" : snapshot.conversationSummary()
            );
        } catch (RuntimeException ex) {
            log.warn("Disk memory unavailable, conversationId={}", request.trustedRequest().getSessionId());
            return new LocalContextSnapshot("", "");
        }
    }

    private String appendRuntimeContext(String existingPrompt, String localContext) {
        return hasText(existingPrompt) ? existingPrompt + "\n\n" + localContext : localContext;
    }

    private OrchestrationTargets loadOrchestrationTargets(
            CurrentUser currentUser,
            ValidatedPhase2Request request
    ) {
        if ("DIRECT".equals(request.executionMode())) {
            return new OrchestrationTargets(loadCandidateSnapshot(currentUser, request), MasterPersona.none());
        }
        if (request.teamId() != null && !request.teamId().isBlank()) {
            return withSystemResourceBuilder(loadTeamTargets(currentUser, request.teamId()), request.trustedRequest().getQuery());
        }
        return withSystemResourceBuilder(
                new OrchestrationTargets(loadCandidateSnapshot(currentUser, request), MasterPersona.none()),
                request.trustedRequest().getQuery()
        );
    }

    /**
     * The hidden builder is always visible to the planner for an orchestrated request.
     * The planner model, not a keyword matcher in the transport layer, decides whether
     * a user is asking to create a new Agent or Team and assigns this candidate only then.
     */
    private OrchestrationTargets withSystemResourceBuilder(OrchestrationTargets targets, String query) {
        List<AgentCapabilitySummary> candidates = new java.util.ArrayList<>(targets.candidates());
        if (candidates.stream().noneMatch(candidate -> SystemResourceBuilder.isSystemAgent(candidate.agentId()))) {
            candidates.add(SystemResourceBuilder.candidate());
        }
        return new OrchestrationTargets(List.copyOf(candidates), targets.masterPersona());
    }

    private OrchestrationTargets loadTeamTargets(CurrentUser currentUser, String teamId) {
        TeamRuntimeCatalogPort teamPort = teamRuntimeCatalogPortProvider.getIfAvailable();
        if (teamPort == null) {
            throw new AgentBridgeException(MvpErrorCode.INTERNAL_ERROR, "Team runtime catalog is not available");
        }
        try {
            TeamRuntimeSelection selection = teamPort.resolve(currentUser, teamId);
            return new OrchestrationTargets(
                    List.copyOf(selection.memberCandidates()),
                    selection.masterPersona()
            );
        } catch (Phase2ContractException error) {
            throw new AgentBridgeException(error.errorCode(), error.getMessage(), error);
        } catch (RuntimeException error) {
            throw AgentBridgeErrorMapper.asAgentBridgeException(error, MvpErrorCode.INTERNAL_ERROR);
        }
    }

    private List<TeamCapabilitySummary> listAvailableTeams(CurrentUser currentUser) {
        TeamRuntimeCatalogPort teamPort = teamRuntimeCatalogPortProvider.getIfAvailable();
        if (teamPort == null) {
            return List.of();
        }
        try {
            List<TeamCapabilitySummary> teams = teamPort.listAvailable(currentUser);
            return teams == null ? List.of() : List.copyOf(teams);
        } catch (Phase2ContractException error) {
            throw new AgentBridgeException(error.errorCode(), error.getMessage(), error);
        } catch (RuntimeException error) {
            throw AgentBridgeErrorMapper.asAgentBridgeException(error, MvpErrorCode.INTERNAL_ERROR);
        }
    }

    private List<AgentCapabilitySummary> loadCandidateSnapshot(
            CurrentUser currentUser,
            ValidatedPhase2Request request
    ) {
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

    private void applySelectedModel(CurrentUser currentUser, GptQueryReq request) {
        if (request != null) {
            RequestTokenUsage.setBillingRequestId(request.getRequestId());
        }
        ModelCatalogService catalog = modelCatalogServiceProvider.getIfAvailable();
        if (catalog == null || currentUser == null || request == null) {
            return;
        }
        try {
            String resolvedName = catalog.resolveRuntimeName(
                    currentUser.tenantId(),
                    currentUser.userId(),
                    request.getModelName()
            );
            var settings = catalog.resolveRuntimeSettings(
                    currentUser.tenantId(),
                    currentUser.userId(),
                    request.getModelName()
            );
            request.setModelName(resolvedName);
            request.setRuntimeTenantId(currentUser.tenantId());
            request.setRuntimeOwnerId(currentUser.userId());
            RequestScopedLlmSettings.set(settings);
        } catch (Phase2ContractException error) {
            throw new AgentBridgeException(error.errorCode(), error.getMessage(), error);
        }
    }

    private ChatAttachmentPrompt.Prompts attachmentPrompts(CurrentUser currentUser, GptQueryReq request) {
        List<String> attachmentIds = request.getAttachmentIds();
        if (attachmentIds == null || attachmentIds.isEmpty()) {
            String query = request.getQuery();
            return new ChatAttachmentPrompt.Prompts(query, query);
        }
        ConversationAttachmentService service = attachmentServiceProvider.getIfAvailable();
        if (service == null) {
            String query = request.getQuery();
            return new ChatAttachmentPrompt.Prompts(query, query);
        }
        return service.preparePrompts(
                currentUser,
                request.getSessionId(),
                attachmentIds,
                request.getQuery()
        );
    }

    private void applyChatAttachments(CurrentUser currentUser, GptQueryReq request) {
        List<String> attachmentIds = request.getAttachmentIds();
        if (attachmentIds == null || attachmentIds.isEmpty()) {
            return;
        }
        ConversationAttachmentService service = attachmentServiceProvider.getIfAvailable();
        if (service == null) {
            return;
        }
        request.setQuery(service.enrichQuery(
                currentUser,
                request.getSessionId(),
                attachmentIds,
                request.getQuery()
        ));
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

    /** Executor candidates plus the master overlay that will drive planning and summarization. */
    private record OrchestrationTargets(
            List<AgentCapabilitySummary> candidates,
            MasterPersona masterPersona
    ) {
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
