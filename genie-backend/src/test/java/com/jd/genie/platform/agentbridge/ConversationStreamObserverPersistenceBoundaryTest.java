package com.jd.genie.platform.agentbridge;

import com.jd.genie.platform.contract.ConversationExecutionCommand;
import com.jd.genie.platform.contract.ConversationExecutionResult;
import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.MessageCompletionCommand;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.conversation.exception.ConversationException;
import com.jd.genie.platform.contract.support.FakeConversationExecutionPort;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.jd.genie.platform.agentbridge.ObserverTestSupport.ASSISTANT_MESSAGE_ID;
import static com.jd.genie.platform.agentbridge.ObserverTestSupport.USER;
import static com.jd.genie.platform.agentbridge.ObserverTestSupport.event;
import static com.jd.genie.platform.agentbridge.ObserverTestSupport.observer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversationStreamObserverPersistenceBoundaryTest {

    @Test
    void markStreamingFailurePropagatesToThePreStreamOrchestrator() {
        FakeConversationExecutionPort port = new FakeConversationExecutionPort() {
            @Override
            public void markStreaming(CurrentUser currentUser, String assistantMessageId) {
                throw new IllegalStateException("database unavailable");
            }
        };
        ObserverTestSupport.RecordingClientChannel channel =
                new ObserverTestSupport.RecordingClientChannel();
        ConversationStreamObserver stream = observer(port, channel);

        assertThrows(IllegalStateException.class, stream::markStreaming);

        assertEquals(ConversationStreamObserver.TerminalState.OPEN, stream.state());
        assertTrue(port.getCalls().isEmpty());
        assertTrue(channel.events().isEmpty());
        assertTrue(channel.failures().isEmpty());
        assertEquals(0, channel.completionCount());
    }

    @Test
    void failureBeforeStreamingTransitionsPreparedMessageToFailed() {
        FakeConversationExecutionPort port = new FakeConversationExecutionPort();
        port.setPrepareExecutionResult(new ConversationExecutionResult(
                "conversation-1",
                "request-1",
                "user-message-1",
                ASSISTANT_MESSAGE_ID,
                1L
        ));
        port.prepareExecution(USER, new ConversationExecutionCommand(
                "conversation-1",
                "request-1",
                "问题",
                0,
                "docs"
        ));
        ConversationStreamObserver stream = observer(
                port,
                new ObserverTestSupport.RecordingClientChannel()
        );

        assertTrue(stream.onError(new IllegalStateException("startup failed")));

        assertEquals(List.of(
                FakeConversationExecutionPort.CallType.PREPARE_EXECUTION,
                FakeConversationExecutionPort.CallType.FAIL
        ), callTypes(port));
        assertFalse(stream.isStreamingMarked());
    }

    @Test
    void rejectedCompletionPreservesSnapshotErrorCodesFromConversationService() {
        for (MvpErrorCode errorCode : List.of(
                MvpErrorCode.SNAPSHOT_TOO_LARGE,
                MvpErrorCode.SNAPSHOT_INVALID
        )) {
            FakeConversationExecutionPort port = new FakeConversationExecutionPort() {
                @Override
                public void complete(CurrentUser currentUser, MessageCompletionCommand command) {
                    throw new ConversationException(errorCode, "snapshot rejected");
                }
            };
            ObserverTestSupport.RecordingClientChannel channel =
                    new ObserverTestSupport.RecordingClientChannel();
            ConversationStreamObserver stream = observer(port, channel);
            stream.markStreaming();
            stream.onEvent(event("最终回答", true));

            assertTrue(stream.onCompleted());

            assertEquals(ConversationStreamObserver.TerminalState.FAILED, stream.state());
            assertEquals(List.of(
                    FakeConversationExecutionPort.CallType.MARK_STREAMING,
                    FakeConversationExecutionPort.CallType.FAIL
            ), callTypes(port));
            assertEquals(errorCode.name(), port.getCalls().get(1).failureCommand().errorCode());
            assertEquals(errorCode, channel.failures().get(0).errorCode());
            assertEquals(1, channel.completionCount());
        }
    }

    private List<FakeConversationExecutionPort.CallType> callTypes(
            FakeConversationExecutionPort port
    ) {
        return port.getCalls().stream()
                .map(FakeConversationExecutionPort.CallRecord::type)
                .toList();
    }
}
