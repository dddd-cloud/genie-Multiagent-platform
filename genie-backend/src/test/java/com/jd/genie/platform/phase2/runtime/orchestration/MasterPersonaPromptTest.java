package com.jd.genie.platform.phase2.runtime.orchestration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.agent.llm.LLMSettings;
import com.jd.genie.config.GenieConfig;
import com.jd.genie.platform.phase2contract.dto.AgentCapabilitySummary;
import com.jd.genie.platform.phase2contract.dto.MasterPersona;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MasterPersonaPromptTest {
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final String PERSONA_PROMPT = "你是严谨的投研主管，先给结论再给证据。";
    private static final List<AgentCapabilitySummary> CANDIDATES =
        List.of(new AgentCapabilitySummary("agent-member", 1L, "研究员", "做调研"));

    @Test
    void plannerSystemPromptCarriesPersonaBeforePlatformRulesAndOverridesModel() {
        AtomicReference<String> captured = new AtomicReference<>();
        OpenAiOrchestrationModelPort port = port(captured, """
            {"steps":[{"stepId":"step-1","mode":"SINGLE_AGENT","objective":"核对营收","inputRefs":[],"agentId":"agent-member","subTasks":[]}]}
            """);

        port.createPlan("茅台营收多少？", "", "", "", CANDIDATES, 1, Map.of(), Map.of(), persona());

        String body = captured.get();
        assertTrue(body.contains("主 Agent 人设"));
        assertTrue(body.contains("先给结论再给证据"));
        assertTrue(body.contains("平台硬性规则"));
        assertTrue(body.contains("\"model\":\"master-model\""));
        assertTrue(
            body.indexOf("主 Agent 人设") < body.indexOf("平台硬性规则"),
            "persona block must precede the platform rules"
        );
    }

    @Test
    void summarizerSystemPromptCarriesPersona() {
        AtomicReference<String> captured = new AtomicReference<>();
        OpenAiOrchestrationModelPort port = port(captured, "结论：营收约 1221 亿元。");

        port.summarize("茅台营收多少？", "", "", "", evidence(), persona());

        String body = captured.get();
        assertTrue(body.contains("主 Agent 人设"));
        assertTrue(body.contains("先给结论再给证据"));
        assertTrue(body.contains("平台硬性规则"));
    }

    @Test
    void absentPersonaLeavesPromptAndModelUnchanged() {
        AtomicReference<String> captured = new AtomicReference<>();
        OpenAiOrchestrationModelPort port = port(captured, "普通回答。");

        port.summarize("茅台营收多少？", "", "", "", evidence(), MasterPersona.none());

        String body = captured.get();
        assertFalse(body.contains("主 Agent 人设"));
        assertTrue(body.contains("\"model\":\"planner-model\""));
    }

    private List<SummaryEvidence> evidence() {
        return List.of(new SummaryEvidence("step-1", "研究员", "核对营收", "约 1221 亿元。", null));
    }

    private MasterPersona persona() {
        return new MasterPersona("agent-master", "投研主管", PERSONA_PROMPT, "master-model");
    }

    private OpenAiOrchestrationModelPort port(AtomicReference<String> capturedBody, String content) {
        GenieConfig config = mock(GenieConfig.class);
        LLMSettings settings = LLMSettings.builder()
            .model("planner-model")
            .apiKey("test-key")
            .baseUrl("https://planner.test")
            .interfaceUrl("/chat/completions")
            .build();
        when(config.getPlannerModelName()).thenReturn("planner-model");
        when(config.getLlmSettingsMap()).thenReturn(Map.of("planner-model", settings));
        Interceptor scripted = chain -> {
            if (chain.request().body() != null) {
                okio.Buffer buffer = new okio.Buffer();
                chain.request().body().writeTo(buffer);
                capturedBody.set(buffer.readUtf8());
            }
            return new Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(ResponseBody.create(JSON, completion(content)))
                .build();
        };
        return new OpenAiOrchestrationModelPort(
            config,
            new ObjectMapper(),
            new OkHttpClient.Builder().addInterceptor(scripted).build()
        );
    }

    private String completion(String content) throws JsonProcessingException {
        return new ObjectMapper().writeValueAsString(Map.of(
            "choices", List.of(Map.of("message", Map.of("content", content)))
        ));
    }
}
