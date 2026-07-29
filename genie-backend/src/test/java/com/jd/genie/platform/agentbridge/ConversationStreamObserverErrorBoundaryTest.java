package com.jd.genie.platform.agentbridge;

import com.jd.genie.platform.contract.MessageFailureCommand;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.contract.support.FakeConversationExecutionPort;
import org.junit.jupiter.api.Test;

import static com.jd.genie.platform.agentbridge.ObserverTestSupport.USER;
import static com.jd.genie.platform.agentbridge.ObserverTestSupport.event;
import static com.jd.genie.platform.agentbridge.ObserverTestSupport.observer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversationStreamObserverErrorBoundaryTest {

    @Test
    void oversizedFinalSnapshotFailsWithoutPersistingInvalidPartialSnapshot() {
        FakeConversationExecutionPort port = new FakeConversationExecutionPort();
        ObserverTestSupport.RecordingClientChannel channel =
                new ObserverTestSupport.RecordingClientChannel();
        ConversationStreamObserver stream = observer(port, channel, 512, () -> {
        });
        stream.markStreaming();
        stream.onEvent(event("x".repeat(5_000), true));

        assertTrue(stream.onCompleted());

        MessageFailureCommand command = failureCommand(port);
        assertEquals("SNAPSHOT_TOO_LARGE", command.errorCode());
        assertNull(command.partialSnapshotJson());
        assertNull(command.payloadVersion());
        assertEquals(ConversationStreamObserver.TerminalState.FAILED, stream.state());
    }

    @Test
    void invalidEventFailsWithSnapshotInvalid() {
        FakeConversationExecutionPort port = new FakeConversationExecutionPort();
        ObserverTestSupport.RecordingClientChannel channel =
                new ObserverTestSupport.RecordingClientChannel();
        ConversationStreamObserver stream = observer(port, channel);

        assertTrue(stream.onEvent(null));

        assertEquals("SNAPSHOT_INVALID", failureCommand(port).errorCode());
        assertEquals(MvpErrorCode.SNAPSHOT_INVALID, channel.failures().get(0).errorCode());
        assertEquals(ConversationStreamObserver.TerminalState.FAILED, stream.state());
    }

    @Test
    void persistenceStillTerminatesWhenCancellationAndClientCleanupFail() {
        FakeConversationExecutionPort port = new FakeConversationExecutionPort();
        ObserverTestSupport.RecordingClientChannel channel =
                new ObserverTestSupport.RecordingClientChannel();
        channel.failFailureSendWith(new IllegalStateException("send failed"));
        channel.failCompletionWith(new IllegalStateException("complete failed"));
        ConversationStreamObserver stream = observer(
                port,
                channel,
                SnapshotPruner.DEFAULT_MAX_BYTES,
                () -> {
                    throw new IllegalStateException("cancel failed");
                }
        );

        assertTrue(stream.onError(new IllegalStateException("downstream failed")));

        assertEquals("AGENT_DOWNSTREAM_ERROR", failureCommand(port).errorCode());
        assertEquals(ConversationStreamObserver.TerminalState.FAILED, stream.state());
        assertEquals(1, channel.completionCount());
    }

    @Test
    void errorMessagesUseSafeFallbackAndFrozenLengthLimit() {
        FakeConversationExecutionPort nullErrorPort = new FakeConversationExecutionPort();
        ConversationStreamObserver nullErrorStream = observer(
                nullErrorPort,
                new ObserverTestSupport.RecordingClientChannel()
        );
        nullErrorStream.onError(null);
        assertEquals("Agent downstream stream failed", failureCommand(nullErrorPort).errorMessage());

        FakeConversationExecutionPort longErrorPort = new FakeConversationExecutionPort();
        ConversationStreamObserver longErrorStream = observer(
                longErrorPort,
                new ObserverTestSupport.RecordingClientChannel()
        );
        longErrorStream.onError(new IllegalStateException("x".repeat(1_200)));
        assertEquals(1_000, failureCommand(longErrorPort).errorMessage().length());
    }

    @Test
    void constructorRejectsInvalidRequiredDependenciesAndLimit() {
        FakeConversationExecutionPort port = new FakeConversationExecutionPort();
        StreamPersistenceObserver persistence = new StreamPersistenceObserver(
                port,
                USER,
                "assistant-1"
        );
        ObserverTestSupport.RecordingClientChannel channel =
                new ObserverTestSupport.RecordingClientChannel();

        assertThrows(
                IllegalArgumentException.class,
                () -> new ConversationStreamObserver(persistence, channel, 0, () -> {
                })
        );
        assertThrows(
                NullPointerException.class,
                () -> new ConversationStreamObserver(null, channel)
        );
        assertThrows(
                NullPointerException.class,
                () -> new ConversationStreamObserver(persistence, null)
        );
    }

    private MessageFailureCommand failureCommand(FakeConversationExecutionPort port) {
        return port.getCalls().stream()
                .filter(call -> call.type() == FakeConversationExecutionPort.CallType.FAIL)
                .findFirst()
                .orElseThrow()
                .failureCommand();
    }
}
