package com.jd.genie.platform.agentbridge;

import com.alibaba.fastjson.JSON;
import com.jd.genie.model.req.AgentRequest;
import com.jd.genie.model.response.GptProcessResult;
import com.jd.genie.platform.contract.MessageCompletionCommand;
import com.jd.genie.platform.contract.support.FakeConversationExecutionPort;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.jd.genie.platform.agentbridge.MultiAgentServiceTestSupport.INTERNAL_TOKEN;
import static com.jd.genie.platform.agentbridge.MultiAgentServiceTestSupport.respond;
import static com.jd.genie.platform.agentbridge.MultiAgentServiceTestSupport.returning;
import static com.jd.genie.platform.agentbridge.MultiAgentServiceTestSupport.scenario;
import static com.jd.genie.platform.agentbridge.MultiAgentServiceTestSupport.stream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiAgentServiceSuccessTest {

    @Test
    void orderedSseEventsCompleteThroughObserverAndCloseResponseBody() throws Exception {
        GptProcessResult running = ObserverTestSupport.event("partial", false);
        GptProcessResult completed = ObserverTestSupport.event("answer", true);
        AtomicInteger handled = new AtomicInteger();
        MultiAgentServiceTestSupport.TrackingResponseBody body =
                MultiAgentServiceTestSupport.trackedStream(
                        ": keep-alive\n\n"
                                + "event: message\n"
                                + "data: {\"messageType\":\"agent_stream\",\n"
                                + "data: \"finish\":false}\n\n"
                                + "data:{\"messageType\":\"result\",\"finish\":true}\n\n"
                );
        MultiAgentServiceTestSupport.Scenario scenario = scenario(
                respond(200, body),
                (request, response, responses, eventResult) ->
                        handled.getAndIncrement() == 0 ? running : completed
        );

        scenario.start();

        assertEquals(ConversationStreamObserver.TerminalState.COMPLETED, scenario.observer().state());
        assertEquals(List.of(running, completed), scenario.channel().events());
        assertEquals(List.of(
                FakeConversationExecutionPort.CallType.MARK_STREAMING,
                FakeConversationExecutionPort.CallType.COMPLETE
        ), callTypes(scenario));
        MessageCompletionCommand completion = scenario.port().getCalls().get(1).completionCommand();
        assertEquals("answer", completion.finalContent());
        assertEquals(2, scenario.observer().bufferedEventCount());
        assertEquals(1, scenario.channel().completionCount());
        assertTrue(body.isClosed());
    }

    @Test
    void heartbeatIsForwardedButExcludedFromSnapshot() {
        GptProcessResult completed = ObserverTestSupport.event("answer", true);
        MultiAgentServiceTestSupport.Scenario scenario = scenario(
                respond(
                        200,
                        stream(
                                "data: heartbeat\n\n"
                                        + "data: {\"messageType\":\"result\",\"finish\":true}\n\n"
                        )
                ),
                returning(completed)
        );

        scenario.start();

        assertEquals(ConversationStreamObserver.TerminalState.COMPLETED, scenario.observer().state());
        assertEquals(List.of("heartbeat", "result"), scenario.channel().events().stream()
                .map(GptProcessResult::getPackageType)
                .toList());
        assertEquals(1, scenario.observer().bufferedEventCount());
    }

    @Test
    void internalRequestCarriesFrozenTokenTraceIdAndTrustedHistory() throws Exception {
        GptProcessResult completed = ObserverTestSupport.event("answer", true);
        MultiAgentServiceTestSupport.Scenario scenario = scenario(
                respond(
                        200,
                        stream("data: {\"messageType\":\"result\",\"finish\":true}\n\n")
                ),
                returning(completed)
        );
        com.jd.genie.model.req.GptQueryReq internalRequest = MultiAgentServiceTestSupport.request();
        internalRequest.setHistoryMessages(List.of(
                AgentRequest.Message.builder().role("user").content("上一轮问题").build(),
                AgentRequest.Message.builder().role("assistant").content("上一轮回答").build()
        ));

        assertTrue(scenario.observer().markStreaming());
        scenario.service().searchForAgentRequest(
                internalRequest,
                scenario.observer(),
                scenario.cancellableCall()
        );

        okhttp3.Request request = scenario.calls().lastCall().request();
        assertEquals(INTERNAL_TOKEN, request.header("X-Genie-Internal-Token"));
        assertEquals("http://127.0.0.1:8080/AutoAgent", request.url().toString());
        assertEquals("POST", request.method());
        okio.Buffer requestBody = new okio.Buffer();
        request.body().writeTo(requestBody);
        String payload = requestBody.readString(StandardCharsets.UTF_8);
        AgentRequest agentRequest = JSON.parseObject(payload, AgentRequest.class);
        assertEquals("trace-1", agentRequest.getRequestId());
        assertEquals("alice", agentRequest.getErp());
        assertEquals("question", agentRequest.getQuery());
        assertEquals(5, agentRequest.getAgentType());
        assertEquals(List.of("user", "assistant"), agentRequest.getMessages().stream()
                .map(AgentRequest.Message::getRole)
                .toList());
        assertEquals(List.of("上一轮问题", "上一轮回答"), agentRequest.getMessages().stream()
                .map(AgentRequest.Message::getContent)
                .toList());
        assertTrue(agentRequest.getIsStream());
        assertFalse(payload.contains("request-1"));
    }

    private List<FakeConversationExecutionPort.CallType> callTypes(
            MultiAgentServiceTestSupport.Scenario scenario
    ) {
        return scenario.port().getCalls().stream()
                .map(FakeConversationExecutionPort.CallRecord::type)
                .toList();
    }
}
