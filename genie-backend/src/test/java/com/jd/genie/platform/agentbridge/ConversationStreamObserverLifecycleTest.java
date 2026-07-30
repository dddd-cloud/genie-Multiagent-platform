package com.jd.genie.platform.agentbridge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.platform.contract.ConversationExecutionCommand;
import com.jd.genie.platform.contract.ConversationExecutionResult;
import com.jd.genie.platform.contract.MessageCompletionCommand;
import com.jd.genie.platform.contract.StreamSnapshotEnvelope;
import com.jd.genie.platform.contract.support.FakeConversationExecutionPort;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.jd.genie.platform.agentbridge.ObserverTestSupport.ASSISTANT_MESSAGE_ID;
import static com.jd.genie.platform.agentbridge.ObserverTestSupport.USER;
import static com.jd.genie.platform.agentbridge.ObserverTestSupport.event;
import static com.jd.genie.platform.agentbridge.ObserverTestSupport.heartbeat;
import static com.jd.genie.platform.agentbridge.ObserverTestSupport.observer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversationStreamObserverLifecycleTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void normalFlowPersistsPrepareStreamingAndCompletionInOrder() throws Exception {
        FakeConversationExecutionPort port = preparedPort();
        ObserverTestSupport.RecordingClientChannel channel =
                new ObserverTestSupport.RecordingClientChannel();
        ConversationStreamObserver stream = observer(port, channel);

        prepare(port);
        assertTrue(stream.markStreaming());
        assertTrue(stream.onEvent(event("部分回答", false)));
        assertTrue(stream.onEvent(heartbeat()));
        assertTrue(stream.onEvent(event("最终回答", true)));
        assertTrue(stream.onCompleted());

        assertEquals(ConversationStreamObserver.TerminalState.COMPLETED, stream.state());
        assertTrue(stream.isTerminal());
        assertTrue(stream.isStreamingMarked());
        assertEquals(2, stream.bufferedEventCount());
        assertEquals(3, channel.events().size());
        assertEquals(0, channel.failures().size());
        assertEquals(1, channel.completionCount());
        assertEquals(List.of(
                FakeConversationExecutionPort.CallType.PREPARE_EXECUTION,
                FakeConversationExecutionPort.CallType.MARK_STREAMING,
                FakeConversationExecutionPort.CallType.COMPLETE
        ), callTypes(port));

        MessageCompletionCommand command = port.getCalls().get(2).completionCommand();
        assertEquals(ASSISTANT_MESSAGE_ID, command.assistantMessageId());
        assertEquals("最终回答", command.finalContent());
        assertEquals(1, command.payloadVersion());
        StreamSnapshotEnvelope snapshot = objectMapper.readValue(
                command.snapshotJson(),
                StreamSnapshotEnvelope.class
        );
        assertEquals(List.of("部分回答", "最终回答"), snapshot.events().stream()
                .map(event -> event.getResponse())
                .toList());
    }

    @Test
    void eachReceivedEventIsForwardedOnceInOrderWhileHeartbeatsStayOutOfSnapshot() throws Exception {
        FakeConversationExecutionPort port = preparedPort();
        ObserverTestSupport.RecordingClientChannel channel =
                new ObserverTestSupport.RecordingClientChannel();
        ConversationStreamObserver stream = observer(port, channel);
        List<com.jd.genie.model.response.GptProcessResult> received = List.of(
                event("第一段", false),
                heartbeat(),
                event("第二段", false),
                heartbeat(),
                event("最终回答", true)
        );

        assertTrue(stream.markStreaming());
        for (com.jd.genie.model.response.GptProcessResult event : received) {
            assertTrue(stream.onEvent(event));
        }
        assertTrue(stream.onCompleted());

        assertEquals(received, channel.events());
        assertEquals(5, channel.events().size());
        assertEquals(3, stream.bufferedEventCount());
        StreamSnapshotEnvelope snapshot = objectMapper.readValue(
                port.getCalls().get(1).completionCommand().snapshotJson(),
                StreamSnapshotEnvelope.class
        );
        assertEquals(List.of("第一段", "第二段", "最终回答"), snapshot.events().stream()
                .map(event -> event.getResponse())
                .toList());
    }

    @Test
    void terminalCallbacksAndDuplicateStreamingMarksAreIgnored() {
        FakeConversationExecutionPort port = preparedPort();
        ObserverTestSupport.RecordingClientChannel channel =
                new ObserverTestSupport.RecordingClientChannel();
        ConversationStreamObserver stream = observer(port, channel);

        assertTrue(stream.markStreaming());
        assertFalse(stream.markStreaming());
        assertTrue(stream.onEvent(event("完成", true)));
        assertTrue(stream.onCompleted());

        assertFalse(stream.onCompleted());
        assertFalse(stream.onError(new IllegalStateException("late error")));
        assertFalse(stream.onClientDisconnected());
        assertFalse(stream.onEvent(event("late", false)));

        assertEquals(List.of(
                FakeConversationExecutionPort.CallType.MARK_STREAMING,
                FakeConversationExecutionPort.CallType.COMPLETE
        ), callTypes(port));
        assertEquals(1, channel.completionCount());
        assertEquals(1, stream.bufferedEventCount());
    }

    private FakeConversationExecutionPort preparedPort() {
        FakeConversationExecutionPort port = new FakeConversationExecutionPort();
        port.setPrepareExecutionResult(new ConversationExecutionResult(
                "conversation-1",
                "request-1",
                "user-message-1",
                ASSISTANT_MESSAGE_ID,
                1L
        ));
        return port;
    }

    private void prepare(FakeConversationExecutionPort port) {
        port.prepareExecution(
                USER,
                new ConversationExecutionCommand(
                        "conversation-1",
                        "request-1",
                        "问题",
                        0,
                        "docs"
                )
        );
    }

    private List<FakeConversationExecutionPort.CallType> callTypes(
            FakeConversationExecutionPort port
    ) {
        return port.getCalls().stream()
                .map(FakeConversationExecutionPort.CallRecord::type)
                .toList();
    }
}
