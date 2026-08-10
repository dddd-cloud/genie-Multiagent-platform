package com.jd.genie.platform.agentbridge;

import com.jd.genie.model.response.GptProcessResult;
import com.jd.genie.platform.contract.MvpErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Objects;

@Slf4j
public final class SseEmitterClientChannel implements ConversationStreamObserver.ClientChannel {
    private final SseEmitter emitter;
    private final String traceId;
    private final Object sendLock;

    public SseEmitterClientChannel(SseEmitter emitter, String traceId) {
        this(emitter, traceId, new Object());
    }

    public SseEmitterClientChannel(SseEmitter emitter, String traceId, Object sendLock) {
        this.emitter = Objects.requireNonNull(emitter, "emitter");
        this.traceId = requireText(traceId, "traceId");
        this.sendLock = Objects.requireNonNull(sendLock, "sendLock");
    }

    @Override
    public void sendEvent(GptProcessResult event) throws IOException {
        synchronized (sendLock) {
            emitter.send(event);
        }
    }

    @Override
    public void sendFailure(MvpErrorCode errorCode, String message) throws IOException {
        Objects.requireNonNull(errorCode, "errorCode");
        GptProcessResult failure = new GptProcessResult();
        failure.setStatus("failed");
        failure.setFinished(true);
        failure.setResponseType("text");
        failure.setTraceId(traceId);
        failure.setReqId(traceId);
        failure.setErrorMsg(requireText(message, "message"));
        synchronized (sendLock) {
            emitter.send(failure);
        }
        log.warn(
                "Agent execution failed, traceId: {}, errorCode: {}",
                traceId,
                errorCode
        );
    }

    @Override
    public void complete() {
        synchronized (sendLock) {
            emitter.complete();
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
