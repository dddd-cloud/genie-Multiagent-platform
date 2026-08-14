package com.jd.genie.platform.agentbridge;

import com.alibaba.fastjson.JSON;
import com.jd.genie.model.req.AgentRequest;
import com.jd.genie.model.req.GptQueryReq;
import com.jd.genie.model.response.GptProcessResult;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.jd.genie.platform.agentbridge.MultiAgentServiceTestSupport.respond;
import static com.jd.genie.platform.agentbridge.MultiAgentServiceTestSupport.returning;
import static com.jd.genie.platform.agentbridge.MultiAgentServiceTestSupport.scenario;
import static com.jd.genie.platform.agentbridge.MultiAgentServiceTestSupport.stream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DirectReactRegressionTest {

    @Test
    void directReactIsNotReRoutedAndKeepsTheExistingStreamLifecycle() throws Exception {
        GptProcessResult completed = ObserverTestSupport.event("react answer", true);
        MultiAgentServiceTestSupport.Scenario scenario = scenario(
                respond(200, stream("data: {\"messageType\":\"result\",\"finish\":true}\n\n")),
                returning(completed)
        );
        GptQueryReq request = MultiAgentServiceTestSupport.request();
        request.setDeepThink(0);
        request.setRuntimeBasePrompt("[UNTRUSTED_LOCAL_CONTEXT] react context");

        scenario.observer().markStreaming();
        scenario.service().searchForAgentRequest(request, scenario.observer(), scenario.cancellableCall());

        AgentRequest internal = bodyAsAgentRequest(scenario);
        assertEquals(5, internal.getAgentType());
        assertEquals("http://127.0.0.1:8080/AutoAgent", scenario.calls().lastCall().request().url().toString());
        assertEquals(ConversationStreamObserver.TerminalState.COMPLETED, scenario.observer().state());
        assertEquals(List.of(completed), scenario.channel().events());
        assertTrue(scenario.channel().events().stream()
                .noneMatch(event -> "orchestration".equals(event.getPackageType())));
    }

    private AgentRequest bodyAsAgentRequest(MultiAgentServiceTestSupport.Scenario scenario) throws Exception {
        okio.Buffer body = new okio.Buffer();
        scenario.calls().lastCall().request().body().writeTo(body);
        return JSON.parseObject(body.readString(StandardCharsets.UTF_8), AgentRequest.class);
    }
}
