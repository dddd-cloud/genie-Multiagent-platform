package com.jd.genie.platform.agentbridge;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class CancellableAgentCall implements Runnable {
    private final AtomicReference<Runnable> cancellation = new AtomicReference<>();
    private final AtomicBoolean cancellationRequested = new AtomicBoolean(false);
    private final AtomicBoolean cancellationInvoked = new AtomicBoolean(false);

    public void bind(Runnable cancellationAction) {
        Objects.requireNonNull(cancellationAction, "cancellationAction");
        if (!cancellation.compareAndSet(null, cancellationAction)) {
            throw new IllegalStateException("Agent call cancellation is already bound");
        }
        invokeIfRequested();
    }

    public void cancel() {
        cancellationRequested.set(true);
        invokeIfRequested();
    }

    @Override
    public void run() {
        cancel();
    }

    public boolean isCancellationRequested() {
        return cancellationRequested.get();
    }

    private void invokeIfRequested() {
        Runnable action = cancellation.get();
        if (action != null
                && cancellationRequested.get()
                && cancellationInvoked.compareAndSet(false, true)) {
            action.run();
        }
    }
}
