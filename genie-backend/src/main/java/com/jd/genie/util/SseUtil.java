package com.jd.genie.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@Slf4j
public class SseUtil {
    public static final long HEARTBEAT_INTERVAL_MS = 10_000L;

    private static final ScheduledExecutorService HEARTBEAT_EXECUTOR =
            Executors.newScheduledThreadPool(2, new ThreadFactory() {
                private final AtomicInteger seq = new AtomicInteger();

                @Override
                public Thread newThread(Runnable runnable) {
                    Thread thread = new Thread(runnable, "sse-heartbeat-" + seq.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                }
            });

    public static SseEmitter build(Long timeout, String requestId) {
        return build(timeout, requestId, ignored -> {
        });
    }

    public static SseEmitter build(
            Long timeout,
            String requestId,
            Consumer<Throwable> disconnectHandler
    ) {
        SseEmitter sseEmitter = create(timeout);
        registerLifecycle(sseEmitter, requestId, disconnectHandler);
        return sseEmitter;
    }

    public static SseEmitter create(Long timeout) {
        return new SseEmitterUTF8(timeout);
    }

    /**
     * Schedule a best-effort heartbeat tick. Failures are logged only — never tear down the SSE.
     */
    public static ScheduledFuture<?> startHeartbeat(String requestId, Runnable tick) {
        Objects.requireNonNull(tick, "tick");
        return HEARTBEAT_EXECUTOR.scheduleAtFixedRate(() -> {
            try {
                tick.run();
            } catch (Exception error) {
                log.warn("SSE heartbeat failed, requestId: {}, error: {}", requestId, error.toString());
            }
        }, HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    public static void registerLifecycle(
            SseEmitter sseEmitter,
            String requestId,
            Consumer<Throwable> disconnectHandler
    ) {
        registerLifecycle(sseEmitter, requestId, disconnectHandler, null);
    }

    public static void registerLifecycle(
            SseEmitter sseEmitter,
            String requestId,
            Consumer<Throwable> disconnectHandler,
            ScheduledFuture<?> heartbeatFuture
    ) {
        Objects.requireNonNull(sseEmitter, "sseEmitter");
        Objects.requireNonNull(disconnectHandler, "disconnectHandler");
        Runnable cancelHeartbeat = () -> {
            if (heartbeatFuture != null) {
                heartbeatFuture.cancel(false);
            }
        };
        sseEmitter.onError(err -> {
            log.error("SseSession Error, requestId: {}", requestId);
            cancelHeartbeat.run();
            notifyDisconnect(disconnectHandler, err, requestId);
            sseEmitter.completeWithError(err);
        });

        sseEmitter.onTimeout(() -> {
            log.info("SseSession Timeout, requestId : {}", requestId);
            cancelHeartbeat.run();
            notifyDisconnect(
                    disconnectHandler,
                    new TimeoutException("SSE session timed out"),
                    requestId
            );
            sseEmitter.complete();
        });

        sseEmitter.onCompletion(() -> {
            cancelHeartbeat.run();
            log.info("SseSession Completion, requestId : {}", requestId);
        });
    }

    private static void notifyDisconnect(
            Consumer<Throwable> disconnectHandler,
            Throwable error,
            String requestId
    ) {
        try {
            disconnectHandler.accept(error);
        } catch (RuntimeException callbackError) {
            log.warn("SseSession disconnect callback failed, requestId: {}", requestId);
        }
    }
}
