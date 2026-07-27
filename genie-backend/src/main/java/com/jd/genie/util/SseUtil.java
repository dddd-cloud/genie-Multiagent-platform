package com.jd.genie.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Objects;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

@Slf4j
public class SseUtil {
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

    public static void registerLifecycle(
            SseEmitter sseEmitter,
            String requestId,
            Consumer<Throwable> disconnectHandler
    ) {
        Objects.requireNonNull(sseEmitter, "sseEmitter");
        Objects.requireNonNull(disconnectHandler, "disconnectHandler");
        sseEmitter.onError(err -> {
            log.error("SseSession Error, requestId: {}", requestId);
            notifyDisconnect(disconnectHandler, err, requestId);
            sseEmitter.completeWithError(err);
        });

        sseEmitter.onTimeout(() -> {
            log.info("SseSession Timeout, requestId : {}", requestId);
            notifyDisconnect(
                    disconnectHandler,
                    new TimeoutException("SSE session timed out"),
                    requestId
            );
            sseEmitter.complete();
        });

        sseEmitter.onCompletion(() ->
                log.info("SseSession Completion, requestId : {}", requestId)
        );
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
