package com.jd.genie.platform.agentbridge;

import com.jd.genie.model.req.GptQueryReq;
import com.jd.genie.platform.contract.ConversationExecutionCommand;
import com.jd.genie.platform.contract.ConversationExecutionResult;
import com.jd.genie.platform.contract.ConversationHistoryItem;
import com.jd.genie.platform.contract.ConversationMessageRole;
import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.MessageFailureCommand;
import com.jd.genie.platform.contract.UserRole;
import com.jd.genie.platform.contract.support.FakeConversationExecutionPort;
import com.jd.genie.platform.contract.support.FakeCurrentUserProvider;
import com.jd.genie.service.IMultiAgentService;
import com.jd.genie.service.impl.GptProcessServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GptProcessServiceOrchestrationTest {
    private static final String CONVERSATION_ID = "123e4567-e89b-12d3-a456-426614174000";
    private static final String ASSISTANT_MESSAGE_ID = "assistant-message-1";
    private static final CurrentUser USER = new CurrentUser(
            "tenant-1",
            "user-1",
            "alice",
            "Alice",
            UserRole.USER
    );

    @Test
    void startsExecutionInFrozenOrderWithTrustedContextAndMappedHistory() {
        List<String> order = new ArrayList<>();
        RecordingPort port = preparedPort(order);
        port.setLoadCompletedHistoryResult(List.of(
                new ConversationHistoryItem(1L, ConversationMessageRole.USER, "上一轮问题"),
                new ConversationHistoryItem(1L, ConversationMessageRole.ASSISTANT, "上一轮回答")
        ));
        RecordingAgentService agent = new RecordingAgentService(order);
        GptProcessServiceImpl service = service(port, agent, order);
        GptQueryReq external = request();
        external.setUser("attacker");
        external.setTraceId("client-trace");

        SseEmitter emitter = service.queryMultiAgentIncrStream(external);

        assertNotNull(emitter);
        assertEquals(List.of("USER", "PREPARE", "HISTORY", "STREAMING", "AGENT"), order);
        assertEquals(List.of(
                FakeConversationExecutionPort.CallType.PREPARE_EXECUTION,
                FakeConversationExecutionPort.CallType.LOAD_COMPLETED_HISTORY,
                FakeConversationExecutionPort.CallType.MARK_STREAMING
        ), callTypes(port));
        ConversationExecutionCommand command = port.getCalls().get(0).command();
        assertEquals(CONVERSATION_ID, command.conversationId());
        assertEquals("request-1", command.requestId());
        assertEquals("问题", command.query());
        assertEquals(6, port.getCalls().get(1).maxTurns());
        assertEquals(12_000, port.getCalls().get(1).maxCharacters());
        assertEquals("request-1", port.getCalls().get(1).excludeRequestId());

        GptQueryReq internal = agent.request;
        assertEquals("alice", internal.getUser());
        assertEquals("alice" + CONVERSATION_ID + ":request-1", internal.getTraceId());
        assertEquals(List.of("user", "assistant"), internal.getHistoryMessages().stream()
                .map(message -> message.getRole())
                .toList());
        assertEquals("attacker", external.getUser());
        assertEquals("client-trace", external.getTraceId());
    }

    @Test
    void validationFailureStopsBeforePrepareAndAgentStart() {
        List<String> order = new ArrayList<>();
        RecordingPort port = preparedPort(order);
        RecordingAgentService agent = new RecordingAgentService(order);
        GptProcessServiceImpl service = service(port, agent, order);
        GptQueryReq invalid = request();
        invalid.setSessionId("invalid");

        AgentBridgeException error = assertThrows(
                AgentBridgeException.class,
                () -> service.queryMultiAgentIncrStream(invalid)
        );

        assertEquals("VALIDATION_ERROR", error.getErrorCode().name());
        assertEquals(List.of("USER"), order);
        assertTrue(port.getCalls().isEmpty());
        assertEquals(0, agent.startCount);
    }

    @Test
    void prepareFailureReliesOnTransactionRollbackAndDoesNotCallFail() {
        List<String> order = new ArrayList<>();
        RuntimeException prepareError = new IllegalStateException("prepare rejected");
        RecordingPort port = new RecordingPort(order) {
            @Override
            public ConversationExecutionResult prepareExecution(
                    CurrentUser currentUser,
                    ConversationExecutionCommand command
            ) {
                order.add("PREPARE");
                throw prepareError;
            }
        };
        RecordingAgentService agent = new RecordingAgentService(order);
        GptProcessServiceImpl service = service(port, agent, order);

        RuntimeException thrown = assertThrows(
                RuntimeException.class,
                () -> service.queryMultiAgentIncrStream(request())
        );

        assertSame(prepareError, thrown);
        assertEquals(List.of("USER", "PREPARE"), order);
        assertTrue(port.getCalls().isEmpty());
        assertEquals(0, agent.startCount);
    }

    @Test
    void historyFailureMarksPreparedMessageFailedBeforeReturningError() {
        List<String> order = new ArrayList<>();
        RuntimeException historyError = new IllegalStateException("history unavailable");
        RecordingPort port = preparedPort(order);
        port.historyError = historyError;
        RecordingAgentService agent = new RecordingAgentService(order);
        GptProcessServiceImpl service = service(port, agent, order);

        RuntimeException thrown = assertThrows(
                RuntimeException.class,
                () -> service.queryMultiAgentIncrStream(request())
        );

        assertSame(historyError, thrown);
        assertEquals(List.of("USER", "PREPARE", "HISTORY", "FAIL"), order);
        assertEquals(List.of(
                FakeConversationExecutionPort.CallType.PREPARE_EXECUTION,
                FakeConversationExecutionPort.CallType.FAIL
        ), callTypes(port));
        MessageFailureCommand failure = port.getCalls().get(1).failureCommand();
        assertEquals(ASSISTANT_MESSAGE_ID, failure.assistantMessageId());
        assertEquals("INTERNAL_ERROR", failure.errorCode());
        assertEquals(0, agent.startCount);
    }

    @Test
    void markStreamingFailureWritesFailedTerminalAndSkipsAgent() {
        List<String> order = new ArrayList<>();
        RecordingPort port = preparedPort(order);
        port.markStreamingError = new IllegalStateException("streaming rejected");
        RecordingAgentService agent = new RecordingAgentService(order);
        GptProcessServiceImpl service = service(port, agent, order);

        SseEmitter emitter = service.queryMultiAgentIncrStream(request());

        assertNotNull(emitter);
        assertEquals(List.of("USER", "PREPARE", "HISTORY", "STREAMING", "FAIL"), order);
        assertEquals(List.of(
                FakeConversationExecutionPort.CallType.PREPARE_EXECUTION,
                FakeConversationExecutionPort.CallType.LOAD_COMPLETED_HISTORY,
                FakeConversationExecutionPort.CallType.FAIL
        ), callTypes(port));
        assertEquals(0, agent.startCount);
    }

    @Test
    void synchronousAgentStartFailureFailsStreamingMessageExactlyOnce() {
        List<String> order = new ArrayList<>();
        RecordingPort port = preparedPort(order);
        RecordingAgentService agent = new RecordingAgentService(order);
        agent.startError = new IllegalStateException("agent start failed");
        GptProcessServiceImpl service = service(port, agent, order);

        SseEmitter emitter = service.queryMultiAgentIncrStream(request());

        assertNotNull(emitter);
        assertEquals(List.of(
                "USER",
                "PREPARE",
                "HISTORY",
                "STREAMING",
                "AGENT",
                "FAIL"
        ), order);
        assertEquals(List.of(
                FakeConversationExecutionPort.CallType.PREPARE_EXECUTION,
                FakeConversationExecutionPort.CallType.LOAD_COMPLETED_HISTORY,
                FakeConversationExecutionPort.CallType.MARK_STREAMING,
                FakeConversationExecutionPort.CallType.FAIL
        ), callTypes(port));
        assertEquals(1, agent.startCount);
    }

    private GptProcessServiceImpl service(
            RecordingPort port,
            RecordingAgentService agent,
            List<String> order
    ) {
        FakeCurrentUserProvider users = new FakeCurrentUserProvider(USER) {
            @Override
            public CurrentUser requireCurrentUser() {
                order.add("USER");
                return super.requireCurrentUser();
            }
        };
        return new GptProcessServiceImpl(
                agent,
                users,
                port,
                3_600_000L,
                8_388_608L,
                6,
                12_000
        );
    }

    private RecordingPort preparedPort(List<String> order) {
        RecordingPort port = new RecordingPort(order);
        port.setPrepareExecutionResult(new ConversationExecutionResult(
                CONVERSATION_ID,
                "request-1",
                "user-message-1",
                ASSISTANT_MESSAGE_ID,
                1L
        ));
        return port;
    }

    private GptQueryReq request() {
        return GptQueryReq.builder()
                .sessionId(CONVERSATION_ID)
                .requestId("request-1")
                .query("问题")
                .deepThink(0)
                .outputStyle("docs")
                .build();
    }

    private List<FakeConversationExecutionPort.CallType> callTypes(
            FakeConversationExecutionPort port
    ) {
        return port.getCalls().stream()
                .map(FakeConversationExecutionPort.CallRecord::type)
                .toList();
    }

    private static class RecordingPort extends FakeConversationExecutionPort {
        private final List<String> order;
        private RuntimeException historyError;
        private RuntimeException markStreamingError;

        private RecordingPort(List<String> order) {
            this.order = order;
        }

        @Override
        public ConversationExecutionResult prepareExecution(
                CurrentUser currentUser,
                ConversationExecutionCommand command
        ) {
            order.add("PREPARE");
            return super.prepareExecution(currentUser, command);
        }

        @Override
        public List<ConversationHistoryItem> loadCompletedHistory(
                CurrentUser currentUser,
                String conversationId,
                String excludeRequestId,
                int maxTurns,
                int maxCharacters
        ) {
            order.add("HISTORY");
            if (historyError != null) {
                throw historyError;
            }
            return super.loadCompletedHistory(
                    currentUser,
                    conversationId,
                    excludeRequestId,
                    maxTurns,
                    maxCharacters
            );
        }

        @Override
        public void markStreaming(CurrentUser currentUser, String assistantMessageId) {
            order.add("STREAMING");
            if (markStreamingError != null) {
                throw markStreamingError;
            }
            super.markStreaming(currentUser, assistantMessageId);
        }

        @Override
        public void fail(CurrentUser currentUser, MessageFailureCommand command) {
            order.add("FAIL");
            super.fail(currentUser, command);
        }
    }

    private static class RecordingAgentService implements IMultiAgentService {
        private final List<String> order;
        private GptQueryReq request;
        private RuntimeException startError;
        private int startCount;

        private RecordingAgentService(List<String> order) {
            this.order = order;
        }

        @Override
        public void searchForAgentRequest(
                GptQueryReq gptQueryReq,
                ConversationStreamObserver observer,
                CancellableAgentCall cancellableCall
        ) {
            order.add("AGENT");
            request = gptQueryReq;
            startCount++;
            if (startError != null) {
                throw startError;
            }
        }
    }
}
