package com.jd.genie.platform.agentbridge;

import com.alibaba.fastjson.JSON;
import com.jd.genie.model.req.AgentRequest;
import com.jd.genie.model.req.GptQueryReq;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static com.jd.genie.platform.agentbridge.MultiAgentServiceTestSupport.pending;
import static com.jd.genie.platform.agentbridge.MultiAgentServiceTestSupport.returning;
import static com.jd.genie.platform.agentbridge.MultiAgentServiceTestSupport.scenario;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DirectReactAdapterTest {

    @Test
    void directReactUsesExistingAutoAgentEndpointWithRuntimeBasePromptOnly() throws Exception {
        MultiAgentServiceTestSupport.Scenario scenario = scenario(
                pending(),
                returning(ObserverTestSupport.event("unused", true))
        );
        GptQueryReq request = MultiAgentServiceTestSupport.request();
        request.setDeepThink(0);
        request.setRuntimeBasePrompt("[UNTRUSTED_LOCAL_CONTEXT] react context");
        request.setRuntimeSopPrompt("must-not-reach-react");

        scenario.observer().markStreaming();
        scenario.service().searchForAgentRequest(request, scenario.observer(), scenario.cancellableCall());

        AgentRequest internal = bodyAsAgentRequest(scenario);
        assertEquals("http://127.0.0.1:8080/AutoAgent", scenario.calls().lastCall().request().url().toString());
        assertEquals(5, internal.getAgentType());
        assertEquals("[UNTRUSTED_LOCAL_CONTEXT] react context", internal.getBasePrompt());
        assertEquals("", internal.getSopPrompt());
    }

    private AgentRequest bodyAsAgentRequest(MultiAgentServiceTestSupport.Scenario scenario) throws Exception {
        okio.Buffer body = new okio.Buffer();
        scenario.calls().lastCall().request().body().writeTo(body);
        return JSON.parseObject(body.readString(StandardCharsets.UTF_8), AgentRequest.class);
    }
}
