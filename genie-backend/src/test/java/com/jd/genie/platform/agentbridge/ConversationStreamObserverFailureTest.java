package com.jd.genie.platform.agentbridge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.platform.contract.MessageFailureCommand;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.contract.StreamSnapshotEnvelope;
import com.jd.genie.platform.contract.support.FakeConversationExecutionPort;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.jd.genie.platform.agentbridge.ObserverTestSupport.event;
import static com.jd.genie.platform.agentbridge.ObserverTestSupport.observer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversationStreamObserverFailureTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void downstreamFailureCancelsCallSendsFailureAndPersistsPartialSnapshot() throws Exception {
        FakeConversationExecutionPort port = new FakeConversationExecutionPort();
        ObserverTestSupport.RecordingClientChannel channel =
                new ObserverTestSupport.RecordingClientChannel();
        AtomicInteger cancellations = new AtomicInteger();
        ConversationStreamObserver stream = observer(
                port,
                channel,
                SnapshotPruner.DEFAULT_MAX_BYTES,
                cancellations::incrementAndGet
        );
        stream.markStreaming();
        stream.onEvent(event("部分回答", false));

        assertTrue(stream.onError(new IllegalStateException("downstream unavailable")));

        assertEquals(ConversationStreamObserver.TerminalState.FAILED, stream.state());
        assertEquals(1, cancellations.get());
        assertEquals(1, channel.completionCount());
        assertEquals(MvpErrorCode.AGENT_DOWNSTREAM_ERROR, channel.failures().get(0).errorCode());
        MessageFailureCommand command = failureCommand(port);
        assertEquals("AGENT_DOWNSTREAM_ERROR", command.errorCode());
        assertEquals("downstream unavailable", command.errorMessage());
        assertEquals(1, command.payloadVersion());
        StreamSnapshotEnvelope partial = objectMapper.readValue(
                command.partialSnapshotJson(),
                StreamSnapshotEnvelope.class
        );
        assertEquals(List.of("部分回答"), partial.events().stream()
                .map(item -> item.getResponse())
                .toList());
    }

    @Test
    void recognizableBridgeErrorsKeepTheirFrozenCodes() {
        List<MvpErrorCode> codes = List.of(
                MvpErrorCode.AGENT_NO_FINAL_EVENT,
                MvpErrorCode.SNAPSHOT_TOO_LARGE,
                MvpErrorCode.SNAPSHOT_INVALID,
                MvpErrorCode.AGENT_STREAM_INTERRUPTED
        );

        for (MvpErrorCode code : codes) {
            FakeConversationExecutionPort port = new FakeConversationExecutionPort();
            ObserverTestSupport.RecordingClientChannel channel =
                    new ObserverTestSupport.RecordingClientChannel();
            ConversationStreamObserver stream = observer(port, channel);

            assertTrue(stream.onError(new AgentBridgeException(code, "mapped")));

            assertEquals(code.name(), failureCommand(port).errorCode());
            assertEquals(code, channel.failures().get(0).errorCode());
            assertEquals(ConversationStreamObserver.TerminalState.FAILED, stream.state());
        }
    }

    @Test
    void missingFinalAnswerFailsInsteadOfCompleting() {
        FakeConversationExecutionPort port = new FakeConversationExecutionPort();
        ObserverTestSupport.RecordingClientChannel channel =
                new ObserverTestSupport.RecordingClientChannel();
        ConversationStreamObserver stream = observer(port, channel);
        stream.markStreaming();
        stream.onEvent(event("部分回答", false));

        assertTrue(stream.onCompleted());

        assertEquals(ConversationStreamObserver.TerminalState.FAILED, stream.state());
        assertEquals("AGENT_NO_FINAL_EVENT", failureCommand(port).errorCode());
        assertFalse(port.getCalls().stream()
                .anyMatch(call -> call.type() == FakeConversationExecutionPort.CallType.COMPLETE));
    }

    @Test
    void clientDisconnectInterruptsAndBlocksLaterCompletion() {
        FakeConversationExecutionPort port = new FakeConversationExecutionPort();
        ObserverTestSupport.RecordingClientChannel channel =
                new ObserverTestSupport.RecordingClientChannel();
        AtomicInteger cancellations = new AtomicInteger();
        ConversationStreamObserver stream = observer(
                port,
                channel,
                SnapshotPruner.DEFAULT_MAX_BYTES,
                cancellations::incrementAndGet
        );
        stream.markStreaming();
        stream.onEvent(event("部分回答", false));

        assertTrue(stream.onClientDisconnected());
        assertFalse(stream.onCompleted());

        assertEquals(ConversationStreamObserver.TerminalState.INTERRUPTED, stream.state());
        assertEquals(1, cancellations.get());
        assertEquals(FakeConversationExecutionPort.CallType.INTERRUPT,
                port.getCalls().get(port.getCalls().size() - 1).type());
        MessageFailureCommand command = port.getCalls().get(port.getCalls().size() - 1).failureCommand();
        assertEquals("CLIENT_DISCONNECTED", command.errorCode());
        assertEquals(1, command.payloadVersion());
        assertFalse(port.getCalls().stream()
                .anyMatch(call -> call.type() == FakeConversationExecutionPort.CallType.COMPLETE));
    }

    @Test
    void failedEventSendPreservesEventBeforeInterrupting() throws Exception {
        FakeConversationExecutionPort port = new FakeConversationExecutionPort();
        ObserverTestSupport.RecordingClientChannel channel =
                new ObserverTestSupport.RecordingClientChannel();
        channel.failEventSendWith(new IllegalStateException("client gone"));
        ConversationStreamObserver stream = observer(port, channel);

        assertTrue(stream.onEvent(event("已缓存", false)));

        assertEquals(ConversationStreamObserver.TerminalState.INTERRUPTED, stream.state());
        MessageFailureCommand command = port.getCalls().get(0).failureCommand();
        assertEquals("CLIENT_DISCONNECTED", command.errorCode());
        StreamSnapshotEnvelope partial = objectMapper.readValue(
                command.partialSnapshotJson(),
                StreamSnapshotEnvelope.class
        );
        assertEquals("已缓存", partial.events().get(0).getResponse());
        assertNull(partial.events().get(0).getErrorMsg());
    }

    private MessageFailureCommand failureCommand(FakeConversationExecutionPort port) {
        return port.getCalls().stream()
                .filter(call -> call.type() == FakeConversationExecutionPort.CallType.FAIL)
                .findFirst()
                .orElseThrow()
                .failureCommand();
    }
}
