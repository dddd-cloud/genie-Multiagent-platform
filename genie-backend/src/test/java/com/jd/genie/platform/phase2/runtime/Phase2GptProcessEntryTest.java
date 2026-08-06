package com.jd.genie.platform.phase2.runtime;

import com.jd.genie.platform.agentbridge.AgentBridgeException;
import com.jd.genie.platform.contract.ConversationExecutionResult;
import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.contract.UserRole;
import com.jd.genie.platform.contract.support.FakeConversationExecutionPort;
import com.jd.genie.platform.contract.support.FakeCurrentUserProvider;
import com.jd.genie.platform.phase2.runtime.request.Phase2GptQueryRequest;
import com.jd.genie.platform.phase2contract.dto.AgentCapabilitySummary;
import com.jd.genie.platform.phase2contract.port.AgentRuntimeCatalogPort;
import com.jd.genie.service.IMultiAgentService;
import com.jd.genie.service.impl.GptProcessServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class Phase2GptProcessEntryTest {
    private static final CurrentUser USER = new CurrentUser(
            "tenant-1",
            "user-1",
            "alice",
            "Alice",
            UserRole.USER
    );

    @Test
    void invalidRequestStopsBeforeCandidateReadAndPrepareExecution() {
        FakeConversationExecutionPort executionPort = new FakeConversationExecutionPort();
        RecordingCatalogPort catalogPort = new RecordingCatalogPort();
        GptProcessServiceImpl service = service(executionPort, catalogPort);

        AgentBridgeException error = assertThrows(
                AgentBridgeException.class,
                () -> service.queryPhase2AgentStreamIncr(request("DIRECT", List.of("agent-1")))
        );

        assertEquals(MvpErrorCode.VALIDATION_ERROR, error.getErrorCode());
        assertTrue(catalogPort.calls.isEmpty());
        assertTrue(executionPort.getCalls().isEmpty());
    }

    @Test
    void autoCandidateRequestFailsAtRuntimeStageWithoutLegacyV1Fallback() {
        FakeConversationExecutionPort executionPort = preparedExecutionPort();
        RecordingCatalogPort catalogPort = new RecordingCatalogPort();
        catalogPort.candidates = List.of(summary("agent-1"));
        IMultiAgentService agentService = mock(IMultiAgentService.class);
        GptProcessServiceImpl service = service(executionPort, catalogPort, agentService);

        AgentBridgeException error = assertThrows(
                AgentBridgeException.class,
                () -> service.queryPhase2AgentStreamIncr(request("AUTO", List.of("agent-1")))
        );

        assertEquals(MvpErrorCode.INTERNAL_ERROR, error.getErrorCode());
        assertEquals(List.of(List.of("agent-1")), catalogPort.calls);
        assertEquals(List.of(
                FakeConversationExecutionPort.CallType.PREPARE_EXECUTION,
                FakeConversationExecutionPort.CallType.LOAD_COMPLETED_HISTORY,
                FakeConversationExecutionPort.CallType.MARK_STREAMING,
                FakeConversationExecutionPort.CallType.FAIL
        ), executionPort.getCalls().stream().map(FakeConversationExecutionPort.CallRecord::type).toList());
        verifyNoInteractions(agentService);
    }

    @Test
    void orchestratedWithoutCandidatesFailsBeforePrepareExecution() {
        FakeConversationExecutionPort executionPort = new FakeConversationExecutionPort();
        RecordingCatalogPort catalogPort = new RecordingCatalogPort();
        GptProcessServiceImpl service = service(executionPort, catalogPort);

        AgentBridgeException error = assertThrows(
                AgentBridgeException.class,
                () -> service.queryPhase2AgentStreamIncr(request("ORCHESTRATED", List.of()))
        );

        assertEquals(MvpErrorCode.NO_SUITABLE_AGENT, error.getErrorCode());
        assertEquals(List.of(List.of()), catalogPort.calls);
        assertTrue(executionPort.getCalls().isEmpty());
    }

    @Test
    void autoWithoutCandidatesFallsBackToSharedDirectPath() {
        FakeConversationExecutionPort executionPort = preparedExecutionPort();
        RecordingCatalogPort catalogPort = new RecordingCatalogPort();
        IMultiAgentService agentService = mock(IMultiAgentService.class);
        GptProcessServiceImpl service = service(executionPort, catalogPort, agentService);

        service.queryPhase2AgentStreamIncr(request("AUTO", List.of()));

        assertEquals(List.of(List.of()), catalogPort.calls);
        assertEquals(List.of(
                FakeConversationExecutionPort.CallType.PREPARE_EXECUTION,
                FakeConversationExecutionPort.CallType.LOAD_COMPLETED_HISTORY,
                FakeConversationExecutionPort.CallType.MARK_STREAMING
        ), executionPort.getCalls().stream().map(FakeConversationExecutionPort.CallRecord::type).toList());
        verify(agentService, times(1)).searchForAgentRequest(any(), any(), any());
    }

    @Test
    void directSkipsCandidateSnapshotAndStartsSharedAutoAgentPath() {
        FakeConversationExecutionPort executionPort = preparedExecutionPort();
        RecordingCatalogPort catalogPort = new RecordingCatalogPort();
        IMultiAgentService agentService = mock(IMultiAgentService.class);
        GptProcessServiceImpl service = service(executionPort, catalogPort, agentService);

        service.queryPhase2AgentStreamIncr(request("DIRECT", List.of()));

        assertTrue(catalogPort.calls.isEmpty());
        assertEquals(List.of(
                FakeConversationExecutionPort.CallType.PREPARE_EXECUTION,
                FakeConversationExecutionPort.CallType.LOAD_COMPLETED_HISTORY,
                FakeConversationExecutionPort.CallType.MARK_STREAMING
        ), executionPort.getCalls().stream().map(FakeConversationExecutionPort.CallRecord::type).toList());
        verify(agentService, times(1)).searchForAgentRequest(any(), any(), any());
    }

    @Test
    void oversizedLocalContextStopsBeforeCandidateReadAndPrepareExecution() {
        FakeConversationExecutionPort executionPort = new FakeConversationExecutionPort();
        RecordingCatalogPort catalogPort = new RecordingCatalogPort();
        GptProcessServiceImpl service = service(executionPort, catalogPort);
        Phase2GptQueryRequest request = request("AUTO", List.of());
        request.getLocalContext().setLongTermMemory("x".repeat(12_001));

        AgentBridgeException error = assertThrows(
                AgentBridgeException.class,
                () -> service.queryPhase2AgentStreamIncr(request)
        );

        assertEquals(MvpErrorCode.LOCAL_CONTEXT_TOO_LARGE, error.getErrorCode());
        assertTrue(catalogPort.calls.isEmpty());
        assertTrue(executionPort.getCalls().isEmpty());
    }

    private FakeConversationExecutionPort preparedExecutionPort() {
        FakeConversationExecutionPort executionPort = new FakeConversationExecutionPort();
        executionPort.setPrepareExecutionResult(new ConversationExecutionResult(
                "123e4567-e89b-12d3-a456-426614174000",
                "request-1",
                "user-message-1",
                "assistant-message-1",
                1L
        ));
        return executionPort;
    }

    private GptProcessServiceImpl service(
            FakeConversationExecutionPort executionPort,
            AgentRuntimeCatalogPort catalogPort
    ) {
        return service(executionPort, catalogPort, mock(IMultiAgentService.class));
    }

    private GptProcessServiceImpl service(
            FakeConversationExecutionPort executionPort,
            AgentRuntimeCatalogPort catalogPort,
            IMultiAgentService agentService
    ) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("agentRuntimeCatalogPort", catalogPort);
        ObjectProvider<AgentRuntimeCatalogPort> provider = beanFactory.getBeanProvider(AgentRuntimeCatalogPort.class);
        return new GptProcessServiceImpl(
                agentService,
                new FakeCurrentUserProvider(USER),
                executionPort,
                provider,
                3_600_000L,
                8_388_608L,
                6,
                12_000
        );
    }

    private Phase2GptQueryRequest request(String mode, List<String> allowedAgentIds) {
        return Phase2GptQueryRequest.builder()
                .sessionId("123e4567-e89b-12d3-a456-426614174000")
                .requestId("request-1")
                .query("question")
                .executionMode(mode)
                .deepThink(0)
                .outputStyle("docs")
                .allowedAgentIds(allowedAgentIds)
                .localContext(Phase2GptQueryRequest.LocalContext.builder()
                        .schemaVersion(1)
                        .longTermMemory("")
                        .conversationSummary("")
                        .build())
                .build();
    }

    private AgentCapabilitySummary summary(String agentId) {
        return new AgentCapabilitySummary(agentId, 1L, "Agent", "analysis");
    }

    private static final class RecordingCatalogPort implements AgentRuntimeCatalogPort {
        private final List<List<String>> calls = new java.util.ArrayList<>();
        private List<AgentCapabilitySummary> candidates = List.of();

        @Override
        public List<AgentCapabilitySummary> listOnlineCandidates(
                CurrentUser user,
                List<String> allowedAgentIds
        ) {
            calls.add(List.copyOf(allowedAgentIds));
            return candidates;
        }

        @Override
        public com.jd.genie.platform.phase2contract.dto.AgentRuntimeProfile loadOnlineProfile(
                CurrentUser user,
                String agentId
        ) {
            throw new UnsupportedOperationException("not used by Stage 1");
        }
    }
}
