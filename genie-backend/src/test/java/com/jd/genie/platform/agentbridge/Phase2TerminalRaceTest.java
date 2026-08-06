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

class Phase2TerminalRaceTest {

    @Test
    void completionAndFailureRaceCanPersistOnlyOneTerminalOutcome() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 12; round++) {
                FakeConversationExecutionPort port = new FakeConversationExecutionPort();
                ObserverTestSupport.RecordingClientChannel channel = new ObserverTestSupport.RecordingClientChannel();
                ConversationStreamObserver stream = observer(port, channel);
                stream.markStreaming();
                stream.onEvent(event("final answer", true));
                CountDownLatch start = new CountDownLatch(1);
                Future<Boolean> completion = pool.submit(() -> {
                    start.await();
                    return stream.onCompleted();
                });
                Future<Boolean> failure = pool.submit(() -> {
                    start.await();
                    return stream.onError(new IllegalStateException("downstream failure"));
                });

                start.countDown();
                assertNotEquals(completion.get(), failure.get());
                List<FakeConversationExecutionPort.CallType> terminals = port.getCalls().stream()
                        .map(FakeConversationExecutionPort.CallRecord::type)
                        .filter(type -> type == FakeConversationExecutionPort.CallType.COMPLETE
                                || type == FakeConversationExecutionPort.CallType.FAIL
                                || type == FakeConversationExecutionPort.CallType.INTERRUPT)
                        .toList();
                assertEquals(1, terminals.size());
                assertEquals(1, channel.completionCount());
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
