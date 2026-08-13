package com.jd.genie.platform.phase2.runtime.orchestration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.agent.llm.LLMSettings;
import com.jd.genie.config.GenieConfig;
import com.jd.genie.platform.agentbridge.AgentBridgeException;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2.runtime.plan.OrchestrationPlan;
import com.jd.genie.platform.phase2contract.dto.AgentCapabilitySummary;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpenAiOrchestrationModelPortTest {
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final List<AgentCapabilitySummary> CANDIDATES = List.of(
            new AgentCapabilitySummary("agent-a", 1L, "Agent A", "analysis")
    );

    @Test
    void acceptsOnlyTheFixedPlanShapeFromThePlanner() {
        AtomicInteger requests = new AtomicInteger();
        OpenAiOrchestrationModelPort port = port(requests, """
                {"steps":[{"stepId":"step-1","mode":"SINGLE_AGENT","objective":"Analyze the input","inputRefs":[],"agentId":"agent-a","subTasks":[]}]}
                """);

        OrchestrationPlan plan = port.createPlan("question", CANDIDATES, 1, Map.of(), Map.of());

        assertEquals(1, requests.get());
        assertEquals("step-1", plan.steps().get(0).stepId());
        assertEquals("agent-a", plan.steps().get(0).agentId());
    }

    @Test
    void rejectsTwoInvalidPlannerResponsesWithoutSynthesizingAPlan() {
        AtomicInteger requests = new AtomicInteger();
        OpenAiOrchestrationModelPort port = port(
                requests,
                "```json\n{\"steps\":[]}\n```",
                "prefix {\"steps\":[]} suffix"
        );

        AgentBridgeException error = assertThrows(
                AgentBridgeException.class,
                () -> port.createPlan("question", CANDIDATES, 1, Map.of(), Map.of())
        );

        assertEquals(2, requests.get());
        assertEquals(MvpErrorCode.ORCHESTRATION_PLAN_INVALID, error.getErrorCode());
    }

    @Test
    void acceptsOnlyTheThreeFixedReviewDecisions() {
        AtomicInteger requests = new AtomicInteger();
        OpenAiOrchestrationModelPort port = port(requests, "{\"decision\":\"RETRY\"}");

        assertEquals(
                OrchestrationModelPort.ReviewDecision.RETRY,
                port.review("step objective", "safe result", "TOOL_TIMEOUT", true, 0)
        );
        assertEquals(1, requests.get());
    }

    @Test
    void rejectsInvalidReviewDecision() {
        OpenAiOrchestrationModelPort port = port(new AtomicInteger(), "{\"decision\":\"REPLAN\"}");

        AgentBridgeException error = assertThrows(
                AgentBridgeException.class,
                () -> port.review("step objective", "safe result", "TOOL_TIMEOUT", true, 0)
        );

        assertEquals(MvpErrorCode.INTERNAL_ERROR, error.getErrorCode());
    }

    private OpenAiOrchestrationModelPort port(AtomicInteger requests, String... contents) {
        GenieConfig config = mock(GenieConfig.class);
        LLMSettings settings = LLMSettings.builder()
                .model("planner-model")
                .apiKey("test-key")
                .baseUrl("https://planner.test")
                .interfaceUrl("/chat/completions")
                .build();
        when(config.getPlannerModelName()).thenReturn("planner-model");
        when(config.getLlmSettingsMap()).thenReturn(Map.of("planner-model", settings));
        return new OpenAiOrchestrationModelPort(config, new ObjectMapper(), scriptedClient(requests, contents));
    }

    private OkHttpClient scriptedClient(AtomicInteger requests, String... contents) {
        Interceptor scriptedResponse = chain -> {
            int index = Math.min(requests.getAndIncrement(), contents.length - 1);
            return new Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(ResponseBody.create(JSON, completion(contents[index])))
                    .build();
        };
        return new OkHttpClient.Builder().addInterceptor(scriptedResponse).build();
    }

    private String completion(String content) throws JsonProcessingException {
        return new ObjectMapper().writeValueAsString(Map.of(
                "choices", List.of(Map.of("message", Map.of("content", content)))
        ));
    }
}
