package com.jd.genie.platform.agentbridge;

import com.jd.genie.platform.contract.support.FakeConversationExecutionPort;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.jd.genie.platform.agentbridge.ObserverTestSupport.event;
import static com.jd.genie.platform.agentbridge.ObserverTestSupport.observer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ConversationStreamObserverConcurrencyTest {

    @Test
    void concurrentCompletionAndErrorPersistExactlyOneTerminal() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int iteration = 0; iteration < 25; iteration++) {
                FakeConversationExecutionPort port = new FakeConversationExecutionPort();
                ObserverTestSupport.RecordingClientChannel channel =
                        new ObserverTestSupport.RecordingClientChannel();
                ConversationStreamObserver stream = observer(port, channel);
                stream.markStreaming();
                stream.onEvent(event("最终回答", true));

                CountDownLatch start = new CountDownLatch(1);
                Future<Boolean> completion = executor.submit(() -> {
                    start.await();
                    return stream.onCompleted();
                });
                Future<Boolean> failure = executor.submit(() -> {
                    start.await();
                    return stream.onError(new IllegalStateException("downstream"));
                });

                start.countDown();
                boolean completionWon = completion.get();
                boolean failureWon = failure.get();

                assertNotEquals(completionWon, failureWon);
                List<FakeConversationExecutionPort.CallType> terminalCalls = port.getCalls().stream()
                        .map(FakeConversationExecutionPort.CallRecord::type)
                        .filter(type -> type == FakeConversationExecutionPort.CallType.COMPLETE
                                || type == FakeConversationExecutionPort.CallType.FAIL
                                || type == FakeConversationExecutionPort.CallType.INTERRUPT)
                        .toList();
                assertEquals(1, terminalCalls.size());
                assertEquals(1, channel.completionCount());
            }
        } finally {
            executor.shutdownNow();
        }
    }
}
