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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

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
    void directRequestUsesSharedPrepareHistoryStreamingAndAgentStates() {
        FakeConversationExecutionPort executionPort = preparedPort();
        IMultiAgentService agent = mock(IMultiAgentService.class);
        GptProcessServiceImpl service = service(executionPort, agent);

        service.queryPhase2AgentStreamIncr(request());

        assertEquals(List.of(
                FakeConversationExecutionPort.CallType.PREPARE_EXECUTION,
                FakeConversationExecutionPort.CallType.LOAD_COMPLETED_HISTORY,
                FakeConversationExecutionPort.CallType.MARK_STREAMING
        ), executionPort.getCalls().stream().map(FakeConversationExecutionPort.CallRecord::type).toList());
        org.mockito.Mockito.verify(agent).searchForAgentRequest(any(), any(), any());
    }

    @Test
    void synchronousAgentFailureHasOneControlledFailureTerminal() {
        FakeConversationExecutionPort executionPort = preparedPort();
        IMultiAgentService agent = mock(IMultiAgentService.class);
        doThrow(new IllegalStateException("agent unavailable"))
                .when(agent).searchForAgentRequest(any(), any(), any());
        GptProcessServiceImpl service = service(executionPort, agent);

        assertThrows(AgentBridgeException.class, () -> service.queryPhase2AgentStreamIncr(request()));

        assertEquals(1, executionPort.getCalls().stream()
                .filter(call -> call.type() == FakeConversationExecutionPort.CallType.FAIL)
                .count());
        assertEquals(0, executionPort.getCalls().stream()
                .filter(call -> call.type() == FakeConversationExecutionPort.CallType.COMPLETE)
                .count());
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
        return Phase2GptQueryRequest.builder()
                .sessionId("123e4567-e89b-12d3-a456-426614174000")
                .requestId("request-1")
                .query("question")
                .executionMode("DIRECT")
                .deepThink(0)
                .outputStyle("docs")
                .allowedAgentIds(List.of())
                .localContext(Phase2GptQueryRequest.LocalContext.builder()
                        .schemaVersion(1)
                        .longTermMemory("")
                        .conversationSummary("")
                        .build())
                .build();
    }
}
