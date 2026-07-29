package com.jd.genie.platform.agentbridge.acceptance;

import com.alibaba.fastjson.JSON;
import com.jd.genie.model.req.AgentRequest;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
@Profile("mvp-acceptance")
@Order(Ordered.LOWEST_PRECEDENCE)
public final class FakeAgentAcceptanceFilter extends OncePerRequestFilter {
    private static final String AUTO_AGENT_PATH = "/AutoAgent";
    private static final String POST_METHOD = "POST";
    private static final String EVENT_STREAM_CONTENT_TYPE = "text/event-stream";

    private final FakeAgentMode mode;
    private final int eventCount;
    private final long delayMillis;
    private final long maxSnapshotBytes;
    private final FakeAgentEventFactory eventFactory;

    @Autowired
    public FakeAgentAcceptanceFilter(
            @Value("${MVP_FAKE_AGENT_MODE:SUCCESS}") String configuredMode,
            @Value("${MVP_FAKE_AGENT_EVENT_COUNT:5}") int eventCount,
            @Value("${MVP_FAKE_AGENT_DELAY_MS:50}") long delayMillis,
            @Value("${GENIE_STREAM_SNAPSHOT_MAX_BYTES:8388608}") long maxSnapshotBytes
    ) {
        this(
                FakeAgentMode.fromConfiguration(configuredMode),
                eventCount,
                delayMillis,
                maxSnapshotBytes,
                new FakeAgentEventFactory()
        );
    }

    FakeAgentAcceptanceFilter(
            FakeAgentMode mode,
            int eventCount,
            long delayMillis,
            long maxSnapshotBytes,
            FakeAgentEventFactory eventFactory
    ) {
        if (eventCount <= 0) {
            throw new IllegalArgumentException("MVP_FAKE_AGENT_EVENT_COUNT must be positive");
        }
        if (delayMillis < 0) {
            throw new IllegalArgumentException("MVP_FAKE_AGENT_DELAY_MS must not be negative");
        }
        if (maxSnapshotBytes <= 0) {
            throw new IllegalArgumentException("GENIE_STREAM_SNAPSHOT_MAX_BYTES must be positive");
        }
        this.mode = mode;
        this.eventCount = eventCount;
        this.delayMillis = delayMillis;
        this.maxSnapshotBytes = maxSnapshotBytes;
        this.eventFactory = eventFactory;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !POST_METHOD.equalsIgnoreCase(request.getMethod())
                || !AUTO_AGENT_PATH.equals(requestPath(request));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (mode == FakeAgentMode.HTTP_500) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setContentType("application/json");
            return;
        }

        AgentRequest agentRequest = readRequest(request);
        response.setStatus(HttpServletResponse.SC_OK);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(EVENT_STREAM_CONTENT_TYPE);
        response.setHeader("Cache-Control", "no-cache");

        if (mode == FakeAgentMode.DISCONNECT_AFTER_N_EVENTS) {
            response.setHeader("Connection", "close");
        }

        try (PrintWriter writer = response.getWriter()) {
            switch (mode) {
                case SUCCESS -> writeEvents(
                        writer,
                        response,
                        eventFactory.successfulEvents(agentRequest, eventCount),
                        false
                );
                case DISCONNECT_AFTER_N_EVENTS -> writeEvents(
                        writer,
                        response,
                        eventFactory.disconnectEvents(agentRequest, eventCount),
                        false
                );
                case MALFORMED_EVENT -> writeEvent(writer, response, eventFactory.malformedEvent());
                case NO_FINAL_EVENT -> writeEvents(
                        writer,
                        response,
                        eventFactory.noFinalEvents(agentRequest),
                        false
                );
                case SLOW_STREAM -> writeEvents(
                        writer,
                        response,
                        eventFactory.successfulEvents(agentRequest, eventCount),
                        true
                );
                case SNAPSHOT_TOO_LARGE -> writeEvent(
                        writer,
                        response,
                        eventFactory.snapshotTooLargeEvent(agentRequest, maxSnapshotBytes)
                );
                case HTTP_500 -> throw new IllegalStateException("HTTP_500 is handled before opening an SSE response");
            }
        }
    }

    private AgentRequest readRequest(HttpServletRequest request) throws IOException {
        StringBuilder payload = new StringBuilder();
        try (var reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                payload.append(line);
            }
        }
        AgentRequest agentRequest = JSON.parseObject(payload.toString(), AgentRequest.class);
        if (agentRequest == null) {
            throw new IOException("Fake Agent requires a valid AgentRequest body");
        }
        return agentRequest;
    }

    private void writeEvents(
            PrintWriter writer,
            HttpServletResponse response,
            List<String> events,
            boolean delayed
    ) throws IOException {
        for (int index = 0; index < events.size(); index++) {
            writeEvent(writer, response, events.get(index));
            if (delayed && index < events.size() - 1) {
                waitForNextEvent();
            }
        }
    }

    private void writeEvent(PrintWriter writer, HttpServletResponse response, String event) throws IOException {
        writer.write("data: ");
        writer.write(event);
        writer.write("\n\n");
        writer.flush();
        response.flushBuffer();
    }

    private void waitForNextEvent() throws IOException {
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IOException("Fake Agent slow stream was interrupted", error);
        }
    }

    private String requestPath(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath == null || contextPath.isBlank() || !requestUri.startsWith(contextPath)) {
            return requestUri;
        }
        return requestUri.substring(contextPath.length());
    }
}
