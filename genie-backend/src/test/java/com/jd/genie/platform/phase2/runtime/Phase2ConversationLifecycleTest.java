package com.jd.genie.platform.phase2.runtime;

import com.jd.genie.platform.agentbridge.AgentBridgeException;
import com.jd.genie.platform.contract.ConversationExecutionResult;
import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.contract.UserRole;
import com.jd.genie.platform.contract.support.FakeConversationExecutionPort;
import com.jd.genie.platform.contract.support.FakeCurrentUserProvider;
import com.jd.genie.platform.phase2.runtime.request.Phase2GptQueryRequest;
import com.jd.genie.service.IMultiAgentService;
import com.jd.genie.service.impl.GptProcessServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class Phase2ConversationLifecycleTest {
    private static final CurrentUser USER = new CurrentUser(
            "tenant-1", "user-1", "alice", "Alice", UserRole.USER
    );

    @Test
    void validationFailureDoesNotCreateMessageOrConsumeRequest() {
        FakeConversationExecutionPort executionPort = new FakeConversationExecutionPort();
        GptProcessServiceImpl service = service(executionPort, mock(IMultiAgentService.class));
        Phase2GptQueryRequest invalid = request();
        invalid.setExecutionMode("INVALID");

        AgentBridgeException error = assertThrows(
                AgentBridgeException.class,
                () -> service.queryPhase2AgentStreamIncr(invalid)
        );

        assertEquals(MvpErrorCode.VALIDATION_ERROR, error.getErrorCode());
        assertEquals(List.of(), executionPort.getCalls());
    }

    @Test
    void directWithoutAgentDoesNotCreateMessageOrCallLegacyAgent() {
        FakeConversationExecutionPort executionPort = preparedPort();
        IMultiAgentService agent = mock(IMultiAgentService.class);
        GptProcessServiceImpl service = service(executionPort, agent);

        AgentBridgeException error = assertThrows(
                AgentBridgeException.class,
                () -> service.queryPhase2AgentStreamIncr(request(List.of()))
        );

        assertEquals(MvpErrorCode.VALIDATION_ERROR, error.getErrorCode());
        assertEquals(List.of(), executionPort.getCalls());
        verifyNoInteractions(agent);
    }

    @Test
    void directWithAgentDoesNotCallLegacyAgentWhenCatalogMissing() {
        FakeConversationExecutionPort executionPort = preparedPort();
        IMultiAgentService agent = mock(IMultiAgentService.class);
        GptProcessServiceImpl service = service(executionPort, agent);

        AgentBridgeException error = assertThrows(
                AgentBridgeException.class,
                () -> service.queryPhase2AgentStreamIncr(request(List.of("agent-1")))
        );

        assertEquals(MvpErrorCode.INTERNAL_ERROR, error.getErrorCode());
        verifyNoInteractions(agent);
    }

    private GptProcessServiceImpl service(
            FakeConversationExecutionPort executionPort,
            IMultiAgentService agent
    ) {
        return new GptProcessServiceImpl(
                agent,
                new FakeCurrentUserProvider(USER),
                executionPort,
                3_600_000L,
                8_388_608L,
                6,
                12_000
        );
    }

    private FakeConversationExecutionPort preparedPort() {
        FakeConversationExecutionPort port = new FakeConversationExecutionPort();
        port.setPrepareExecutionResult(new ConversationExecutionResult(
                "123e4567-e89b-12d3-a456-426614174000",
                "request-1",
                "user-message-1",
                "assistant-message-1",
                1L
        ));
        return port;
    }

    private Phase2GptQueryRequest request() {
        return request(List.of());
    }

    private Phase2GptQueryRequest request(List<String> allowedAgentIds) {
        return Phase2GptQueryRequest.builder()
                .sessionId("123e4567-e89b-12d3-a456-426614174000")
                .requestId("request-1")
                .query("question")
                .executionMode("DIRECT")
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
}
