package com.jd.genie.platform.agentbridge;

import com.jd.genie.platform.contract.support.FakeConversationExecutionPort;
import com.jd.genie.util.SseUtil;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static com.jd.genie.platform.agentbridge.ObserverTestSupport.event;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SseUtilLifecycleTest {

    @Test
    void timeoutAndLateErrorPersistOneInterruptedTerminal() {
        FakeConversationExecutionPort port = new FakeConversationExecutionPort();
        RecordingSseEmitter emitter = new RecordingSseEmitter();
        AtomicInteger cancellations = new AtomicInteger();
        ConversationStreamObserver stream = new ConversationStreamObserver(
                new StreamPersistenceObserver(port, ObserverTestSupport.USER, "assistant-1"),
                new SseEmitterClientChannel(emitter, "trace-1"),
                SnapshotPruner.DEFAULT_MAX_BYTES,
                cancellations::incrementAndGet
        );
        SseUtil.registerLifecycle(
                emitter,
                "trace-1",
                ignored -> stream.onClientDisconnected()
        );
        assertTrue(stream.markStreaming());

        emitter.triggerTimeout();
        emitter.triggerError(new IllegalStateException("late transport error"));
        emitter.triggerTimeout();

        assertEquals(ConversationStreamObserver.TerminalState.INTERRUPTED, stream.state());
        assertEquals(1, cancellations.get());
        assertEquals(List.of(
                FakeConversationExecutionPort.CallType.MARK_STREAMING,
                FakeConversationExecutionPort.CallType.INTERRUPT
        ), callTypes(port));
    }

    @Test
    void normalCompletionAndLateCallbacksCannotOverwriteCompletedTerminal() {
        FakeConversationExecutionPort port = new FakeConversationExecutionPort();
        RecordingSseEmitter emitter = new RecordingSseEmitter();
        ConversationStreamObserver stream = new ConversationStreamObserver(
                new StreamPersistenceObserver(port, ObserverTestSupport.USER, "assistant-1"),
                new SseEmitterClientChannel(emitter, "trace-1")
        );
        SseUtil.registerLifecycle(
                emitter,
                "trace-1",
                ignored -> stream.onClientDisconnected()
        );
        assertTrue(stream.markStreaming());
        assertTrue(stream.onEvent(event("最终回答", true)));
        assertTrue(stream.onCompleted());

        emitter.triggerCompletion();
        emitter.triggerCompletion();
        emitter.triggerError(new IllegalStateException("late transport error"));

        assertEquals(ConversationStreamObserver.TerminalState.COMPLETED, stream.state());
        assertEquals(List.of(
                FakeConversationExecutionPort.CallType.MARK_STREAMING,
                FakeConversationExecutionPort.CallType.COMPLETE
        ), callTypes(port));
    }

    @Test
    void disconnectCallbackFailureDoesNotBlockEmitterTermination() {
        RecordingSseEmitter emitter = new RecordingSseEmitter();
        SseUtil.registerLifecycle(emitter, "trace-1", ignored -> {
            throw new IllegalStateException("persistence callback failed");
        });

        assertDoesNotThrow(emitter::triggerTimeout);
        assertDoesNotThrow(() -> emitter.triggerError(new IllegalStateException("transport failed")));
        assertEquals(1, emitter.completeCount);
        assertEquals(1, emitter.completeWithErrorCount);
    }

    private List<FakeConversationExecutionPort.CallType> callTypes(
            FakeConversationExecutionPort port
    ) {
        return port.getCalls().stream()
                .map(FakeConversationExecutionPort.CallRecord::type)
                .toList();
    }

    private static final class RecordingSseEmitter extends SseEmitter {
        private Runnable timeoutCallback;
        private Consumer<Throwable> errorCallback;
        private Runnable completionCallback;
        private int completeCount;
        private int completeWithErrorCount;

        private RecordingSseEmitter() {
            super(1_000L);
        }

        @Override
        public void onTimeout(Runnable callback) {
            timeoutCallback = callback;
        }

        @Override
        public void onError(Consumer<Throwable> callback) {
            errorCallback = callback;
        }

        @Override
        public void onCompletion(Runnable callback) {
            completionCallback = callback;
        }

        @Override
        public void complete() {
            completeCount++;
        }

        @Override
        public void completeWithError(Throwable error) {
            completeWithErrorCount++;
        }

        private void triggerTimeout() {
            timeoutCallback.run();
        }

        private void triggerError(Throwable error) {
            errorCallback.accept(error);
        }

        private void triggerCompletion() {
            completionCallback.run();
        }
    }
}
