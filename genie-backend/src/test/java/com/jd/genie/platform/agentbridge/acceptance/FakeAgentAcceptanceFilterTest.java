package com.jd.genie.platform.agentbridge.acceptance;

import com.alibaba.fastjson.JSON;
import com.jd.genie.model.req.AgentRequest;
import com.jd.genie.model.response.AgentResponse;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FakeAgentAcceptanceFilterTest {
    private static final long SNAPSHOT_LIMIT = 512;

    @Test
    void successEmitsCompatibleFinalEvent() throws Exception {
        Invocation invocation = invoke(FakeAgentMode.SUCCESS, 3, 0, SNAPSHOT_LIMIT, postAutoAgentRequest());

        assertSse(invocation.response());
        List<AgentResponse> events = agentResponses(invocation.response());
        assertEquals(3, events.size());
        assertEquals("trace-acceptance", events.get(0).getRequestId());
        assertEquals("5", String.valueOf(events.get(0).getResultMap().get("agentType")));
        assertFalse(events.get(0).getFinish());
        assertTrue(events.get(2).getFinish());
        assertEquals("MVP fake agent completed", events.get(2).getResult());
        assertFalse(invocation.chainCalled().get());
    }

    @Test
    void http500ShortCircuitsWithoutSse() throws Exception {
        Invocation invocation = invoke(FakeAgentMode.HTTP_500, 1, 0, SNAPSHOT_LIMIT, postAutoAgentRequest());

        assertEquals(500, invocation.response().getStatus());
        assertTrue(invocation.response().getContentType().startsWith("application/json"));
        assertEquals(0, invocation.response().getContentAsByteArray().length);
        assertFalse(invocation.chainCalled().get());
    }

    @Test
    void disconnectWritesConfiguredNonFinalEventsThenCloses() throws Exception {
        Invocation invocation = invoke(
                FakeAgentMode.DISCONNECT_AFTER_N_EVENTS,
                2,
                0,
                SNAPSHOT_LIMIT,
                postAutoAgentRequest()
        );

        List<AgentResponse> events = agentResponses(invocation.response());
        assertEquals(2, events.size());
        assertTrue(events.stream().noneMatch(AgentResponse::getFinish));
        assertEquals("close", invocation.response().getHeader("Connection"));
        assertFalse(invocation.chainCalled().get());
    }

    @Test
    void malformedEventKeepsSseEnvelopeButInvalidPayload() throws Exception {
        Invocation invocation = invoke(FakeAgentMode.MALFORMED_EVENT, 1, 0, SNAPSHOT_LIMIT, postAutoAgentRequest());

        assertSse(invocation.response());
        assertEquals(List.of("{malformed}"), payloads(invocation.response()));
        assertThrows(
                RuntimeException.class,
                () -> JSON.parseObject(payloads(invocation.response()).get(0), AgentResponse.class)
        );
    }

    @Test
    void noFinalEventEndsWithDoneWithoutSuccess() throws Exception {
        Invocation invocation = invoke(FakeAgentMode.NO_FINAL_EVENT, 1, 0, SNAPSHOT_LIMIT, postAutoAgentRequest());

        List<String> payloads = payloads(invocation.response());
        assertEquals("[DONE]", payloads.get(1));
        AgentResponse first = JSON.parseObject(payloads.get(0), AgentResponse.class);
        assertFalse(first.getFinish());
    }

    @Test
    void slowStreamAppliesConfiguredDelayBetweenEvents() throws Exception {
        long startedAt = System.nanoTime();
        Invocation invocation = invoke(FakeAgentMode.SLOW_STREAM, 3, 30, SNAPSHOT_LIMIT, postAutoAgentRequest());
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;

        assertEquals(3, agentResponses(invocation.response()).size());
        assertTrue(elapsedMillis >= 40, "slow stream must delay between business events");
    }

    @Test
    void snapshotTooLargeEmitsACompatibleOversizedFinalPayload() throws Exception {
        Invocation invocation = invoke(
                FakeAgentMode.SNAPSHOT_TOO_LARGE,
                1,
                0,
                SNAPSHOT_LIMIT,
                postAutoAgentRequest()
        );

        AgentResponse event = agentResponses(invocation.response()).get(0);
        assertTrue(event.getFinish());
        assertTrue(event.getResult().getBytes(StandardCharsets.UTF_8).length > SNAPSHOT_LIMIT);
    }

    @Test
    void nonAutoAgentRequestsDelegateToTheExistingChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/other");
        Invocation invocation = invoke(FakeAgentMode.SUCCESS, 1, 0, SNAPSHOT_LIMIT, request);

        assertTrue(invocation.chainCalled().get());
        assertEquals(0, invocation.response().getContentAsByteArray().length);
    }

    @Test
    void onlyFixedModeNamesAreAccepted() {
        assertEquals(FakeAgentMode.SLOW_STREAM, FakeAgentMode.fromConfiguration(" slow_stream "));
        assertThrows(IllegalArgumentException.class, () -> FakeAgentMode.fromConfiguration("other"));
        assertThrows(IllegalArgumentException.class, () -> FakeAgentMode.fromConfiguration(" "));
    }

    private Invocation invoke(
            FakeAgentMode mode,
            int eventCount,
            long delayMillis,
            long snapshotLimit,
            MockHttpServletRequest request
    ) throws Exception {
        FakeAgentAcceptanceFilter filter = new FakeAgentAcceptanceFilter(
                mode,
                eventCount,
                delayMillis,
                snapshotLimit,
                new FakeAgentEventFactory()
        );
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean();
        FilterChain chain = (ignoredRequest, ignoredResponse) -> chainCalled.set(true);
        filter.doFilter(request, response, chain);
        return new Invocation(response, chainCalled);
    }

    private MockHttpServletRequest postAutoAgentRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/AutoAgent");
        request.setContentType("application/json");
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());
        request.setContent(JSON.toJSONString(
                AgentRequest.builder()
                        .requestId("trace-acceptance")
                        .agentType(5)
                        .query("acceptance request")
                        .build()
        ).getBytes(StandardCharsets.UTF_8));
        return request;
    }

    private List<AgentResponse> agentResponses(MockHttpServletResponse response) {
        return payloads(response).stream()
                .map(payload -> JSON.parseObject(payload, AgentResponse.class))
                .toList();
    }

    private List<String> payloads(MockHttpServletResponse response) {
        String body = new String(response.getContentAsByteArray(), StandardCharsets.UTF_8);
        return Arrays.stream(body.split("\\n\\n"))
                .filter(block -> !block.isBlank())
                .map(block -> {
                    assertTrue(block.startsWith("data: "));
                    return block.substring("data: ".length());
                })
                .toList();
    }

    private void assertSse(MockHttpServletResponse response) {
        assertEquals(200, response.getStatus());
        assertTrue(response.getContentType().startsWith("text/event-stream"));
    }

    private record Invocation(MockHttpServletResponse response, AtomicBoolean chainCalled) {
    }
}
