package com.jd.genie.util;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.charset.StandardCharsets;

public class SseEmitterUTF8 extends SseEmitter {
    public SseEmitterUTF8(Long timeout) {
        super(timeout);
    }
    @Override
    protected void extendResponse(ServerHttpResponse outputMessage) {
        HttpHeaders headers = outputMessage.getHeaders();
        if (headers.getContentType() == null) {
            headers.setContentType(new MediaType("text", "event-stream", StandardCharsets.UTF_8));
        }
        // Prevent proxies (Vite / nginx) from buffering the live orchestration stream.
        headers.set(HttpHeaders.CACHE_CONTROL, "no-cache, no-transform");
        headers.set("X-Accel-Buffering", "no");
        headers.set(HttpHeaders.CONNECTION, "keep-alive");
    }
}
