package com.jd.genie.platform.agentbridge;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CancellableAgentCallTest {

    @Test
    void cancellationRequestedBeforeBindCancelsCallOnceWhenBound() {
        CancellableAgentCall call = new CancellableAgentCall();
        AtomicInteger cancellations = new AtomicInteger();

        call.cancel();
        call.bind(cancellations::incrementAndGet);
        call.cancel();

        assertTrue(call.isCancellationRequested());
        assertEquals(1, cancellations.get());
    }

    @Test
    void repeatedCancellationAfterBindInvokesActionOnce() {
        CancellableAgentCall call = new CancellableAgentCall();
        AtomicInteger cancellations = new AtomicInteger();
        call.bind(cancellations::incrementAndGet);

        call.run();
        call.cancel();

        assertEquals(1, cancellations.get());
    }

    @Test
    void cancellationCanOnlyBeBoundOnce() {
        CancellableAgentCall call = new CancellableAgentCall();
        call.bind(() -> {
        });

        assertThrows(IllegalStateException.class, () -> call.bind(() -> {
        }));
    }

    @Test
    void bindingAndCancellationRaceStillInvokesCancellationExactlyOnce() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int index = 0; index < 100; index++) {
                CancellableAgentCall call = new CancellableAgentCall();
                AtomicInteger cancellations = new AtomicInteger();
                CountDownLatch start = new CountDownLatch(1);
                Future<?> binding = executor.submit(() -> {
                    start.await();
                    call.bind(cancellations::incrementAndGet);
                    return null;
                });
                Future<?> canceling = executor.submit(() -> {
                    start.await();
                    call.cancel();
                    return null;
                });

                start.countDown();
                binding.get();
                canceling.get();

                assertEquals(1, cancellations.get());
            }
        } finally {
            executor.shutdownNow();
        }
    }
}
