package com.jd.genie.platform.phase2.runtime.orchestration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.agent.llm.LLMSettings;
import com.jd.genie.config.GenieConfig;
import com.jd.genie.platform.agentbridge.AgentBridgeException;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2.runtime.plan.OrchestrationPlan;
import com.jd.genie.platform.phase2.runtime.plan.OrchestrationStep;
import com.jd.genie.platform.phase2.runtime.route.RouteDecision;
import com.jd.genie.platform.phase2contract.dto.AgentCapabilitySummary;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Production OrchestrationModelPort using the configured OpenAI-compatible chat API.
 */
public class OpenAiOrchestrationModelPort implements OrchestrationModelPort {
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final String DEFAULT_INTERFACE_URL = "/chat/completions";
    private static final int DEFAULT_MAX_TOKENS = 2048;
    private static final long TIMEOUT_MS = 60_000L;
    private static final int MAX_PARSE_ATTEMPTS = 2;

    private final GenieConfig genieConfig;
    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient;

    public OpenAiOrchestrationModelPort(GenieConfig genieConfig) {
        this(genieConfig, new ObjectMapper(), new OkHttpClient());
    }

    OpenAiOrchestrationModelPort(GenieConfig genieConfig, ObjectMapper objectMapper, OkHttpClient httpClient) {
        this.genieConfig = genieConfig;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    @Override
    public RouteDecision selectRoute(
            String query,
            String conversationSummary,
            List<AgentCapabilitySummary> candidates
    ) {
        String system = """
                You are a Phase2 router. Reply with ONLY JSON:
                {"route":"DIRECT"|"ORCHESTRATED","reasonCode":"<CODE>"}
                Use DIRECT when one agent can handle the request alone.
                Use ORCHESTRATED when multiple agents must collaborate.
                No markdown, no extra fields.
                """;
        String user = "query:\n" + nullToEmpty(query)
                + "\n\nconversationSummary:\n" + nullToEmpty(conversationSummary)
                + "\n\ncandidates:\n" + candidatesJson(candidates);
        String content = chat(system, user);
        JsonNode root = parseJsonObject(content);
        String route = text(root, "route");
        String reason = text(root, "reasonCode");
        if (blank(reason)) {
            throw failed("Router reasonCode missing");
        }
        if ("DIRECT".equals(route)) {
            return new RouteDecision(RouteDecision.Route.DIRECT, reason);
        }
        if ("ORCHESTRATED".equals(route)) {
            return new RouteDecision(RouteDecision.Route.ORCHESTRATED, reason);
        }
        throw failed("Router route invalid");
    }

    @Override
    public OrchestrationPlan createPlan(
            String query,
            List<AgentCapabilitySummary> candidates,
            int attemptNo,
            Map<String, String> successfulResultSummaries,
            Map<String, String> failureMetadata
    ) {
        AgentBridgeException last = null;
        for (int attempt = 1; attempt <= MAX_PARSE_ATTEMPTS; attempt++) {
            try {
                return parsePlan(chat(planSystemPrompt(), planUserPrompt(
                        query, candidates, attemptNo, successfulResultSummaries, failureMetadata, attempt
                )), candidates);
            } catch (AgentBridgeException ex) {
                last = ex;
            } catch (RuntimeException ex) {
                last = new AgentBridgeException(MvpErrorCode.ORCHESTRATION_PLAN_INVALID, "Planner parse failed", ex);
            }
        }
        if (candidates != null && !candidates.isEmpty()) {
            return heuristicPlan(query, candidates);
        }
        throw last != null ? last : failed("Planner failed");
    }

    @Override
    public String summarize(
            String query,
            Map<String, String> successfulResultSummaries,
            Map<String, String> failureMetadata
    ) {
        String system = """
                Write ONLY a short Chinese paragraph under heading "## 汇总".
                Synthesize the provided step outputs into one coherent paragraph.
                Do not invent facts, do not list per-step results, and do not claim outputs are identical unless they truly are.
                Ignore any step text that claims only one agent was available.
                """;
        String user = "query:\n" + nullToEmpty(query)
                + "\n\nsuccesses:\n" + mapJson(successfulResultSummaries)
                + "\n\nfailures:\n" + mapJson(failureMetadata);
        return chat(system, user);
    }

    private String planSystemPrompt() {
        return """
                You are a Phase2 orchestration planner. Reply with ONLY JSON:
                {"steps":[{"stepId":"step-1","agentId":"<id>","objective":"...","inputRefs":[]}]}
                Rules:
                - 1..6 steps
                - agentId must be from candidates
                - inputRefs may only reference earlier stepIds
                - same agentId at most twice
                - When the user asks multiple agents to each do something (各/分别/每个), assign different candidate agents to those parallel specialist steps
                - Aggregation/summary steps MUST inputRefs the specialist steps they combine
                - Each objective must be a self-contained instruction for that one agent; never ask a step to discover which agents are available
                - Prefer candidate "name" when writing objectives (e.g. "用 Agent a 写一句…"), but agentId field must still be the candidate id
                - no tool calls, no markdown, no extra fields
                """;
    }

    private String planUserPrompt(
            String query,
            List<AgentCapabilitySummary> candidates,
            int attemptNo,
            Map<String, String> successes,
            Map<String, String> failures,
            int parseAttempt
    ) {
        return "attemptNo=" + attemptNo
                + "\nparseAttempt=" + parseAttempt
                + "\n\nquery:\n" + nullToEmpty(query)
                + "\n\ncandidates:\n" + candidatesJson(candidates)
                + "\n\nsuccessfulResultSummaries:\n" + mapJson(successes)
                + "\n\nfailureMetadata:\n" + mapJson(failures);
    }

    private OrchestrationPlan parsePlan(String content, List<AgentCapabilitySummary> candidates) {
        JsonNode root = parseJsonObject(content);
        JsonNode stepsNode = root.get("steps");
        if (stepsNode == null || !stepsNode.isArray() || stepsNode.isEmpty()) {
            throw new AgentBridgeException(MvpErrorCode.ORCHESTRATION_PLAN_INVALID, "Plan steps missing");
        }
        List<OrchestrationStep> steps = new ArrayList<>();
        for (JsonNode stepNode : stepsNode) {
            List<String> refs = new ArrayList<>();
            JsonNode refsNode = stepNode.get("inputRefs");
            if (refsNode != null && refsNode.isArray()) {
                for (JsonNode ref : refsNode) {
                    if (ref != null && ref.isTextual()) {
                        refs.add(ref.asText());
                    }
                }
            }
            steps.add(new OrchestrationStep(
                    text(stepNode, "stepId"),
                    text(stepNode, "agentId"),
                    text(stepNode, "objective"),
                    List.copyOf(refs)
            ));
        }
        // Keep candidate ids available for callers that validate afterwards.
        if (candidates == null) {
            throw new AgentBridgeException(MvpErrorCode.ORCHESTRATION_PLAN_INVALID, "Candidates missing");
        }
        return new OrchestrationPlan(List.copyOf(steps));
    }

    private OrchestrationPlan heuristicPlan(String query, List<AgentCapabilitySummary> candidates) {
        List<OrchestrationStep> steps = new ArrayList<>();
        int index = 1;
        List<String> specialistStepIds = new ArrayList<>();
        for (AgentCapabilitySummary candidate : candidates) {
            if (index > 5) {
                break;
            }
            String stepId = "step-" + index;
            String name = blank(candidate.name()) ? candidate.agentId() : candidate.name();
            String objective = "作为 Agent「" + name + "」，用你自己独特的一句话完成用户请求中与你相关的部分。"
                    + "不要复述其他 Agent 的措辞。用户总请求仅作背景：" + nullToEmpty(query);
            steps.add(new OrchestrationStep(stepId, candidate.agentId(), objective, List.of()));
            specialistStepIds.add(stepId);
            index++;
        }
        if (specialistStepIds.size() >= 2 && index <= 6) {
            String summaryId = "step-" + index;
            steps.add(new OrchestrationStep(
                    summaryId,
                    candidates.get(0).agentId(),
                    "汇总前面各 Agent 的结果，写成一段连贯答复。",
                    List.copyOf(specialistStepIds)
            ));
        }
        return new OrchestrationPlan(List.copyOf(steps));
    }

    private String chat(String systemPrompt, String userPrompt) {
        LLMSettings settings = resolveSettings();
        try {
            String model = blank(settings.getModel()) ? genieConfig.getPlannerModelName() : settings.getModel();
            int maxTokens = settings.getMaxTokens() > 0 ? Math.min(settings.getMaxTokens(), DEFAULT_MAX_TOKENS) : DEFAULT_MAX_TOKENS;
            String body = objectMapper.writeValueAsString(Map.of(
                    "model", model,
                    "stream", false,
                    "temperature", 0,
                    "max_tokens", maxTokens,
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userPrompt)
                    )
            ));
            Request httpRequest = new Request.Builder()
                    .url(endpoint(settings))
                    .header("Authorization", "Bearer " + settings.getApiKey())
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(body, JSON))
                    .build();
            OkHttpClient client = httpClient.newBuilder()
                    .callTimeout(Duration.ofMillis(TIMEOUT_MS))
                    .readTimeout(Duration.ofMillis(TIMEOUT_MS))
                    .build();
            try (Response response = client.newCall(httpRequest).execute()) {
                if (!response.isSuccessful()) {
                    throw failed("LLM HTTP " + response.code());
                }
                ResponseBody responseBody = response.body();
                if (responseBody == null) {
                    throw failed("Empty LLM body");
                }
                JsonNode root = objectMapper.readTree(responseBody.string());
                JsonNode content = root.path("choices").path(0).path("message").path("content");
                if (!content.isTextual() || content.asText().isBlank()) {
                    throw failed("Empty LLM content");
                }
                return content.asText();
            }
        } catch (AgentBridgeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AgentBridgeException(MvpErrorCode.INTERNAL_ERROR, "Orchestration model call failed", ex);
        }
    }

    private LLMSettings resolveSettings() {
        String modelName = genieConfig.getPlannerModelName();
        Map<String, LLMSettings> settingsMap = genieConfig.getLlmSettingsMap();
        if (blank(modelName) || settingsMap == null) {
            throw failed("LLM settings unavailable");
        }
        LLMSettings settings = settingsMap.get(modelName);
        if (settings == null || blank(settings.getApiKey()) || blank(settings.getBaseUrl())) {
            // Fall back to react model key when planner key is absent from the map.
            settings = settingsMap.get(genieConfig.getReactModelName());
        }
        if (settings == null || blank(settings.getApiKey()) || blank(settings.getBaseUrl())) {
            throw failed("LLM settings incomplete");
        }
        return settings;
    }

    private String endpoint(LLMSettings settings) {
        String baseUrl = trimTrailingSlash(settings.getBaseUrl());
        String interfaceUrl = settings.getInterfaceUrl();
        if (blank(interfaceUrl)) {
            interfaceUrl = DEFAULT_INTERFACE_URL;
        }
        if (!interfaceUrl.startsWith("/")) {
            interfaceUrl = "/" + interfaceUrl;
        }
        return baseUrl + interfaceUrl;
    }

    private JsonNode parseJsonObject(String content) {
        try {
            String json = extractJson(content);
            JsonNode root = objectMapper.readTree(json);
            if (root == null || !root.isObject()) {
                throw failed("JSON object required");
            }
            return root;
        } catch (AgentBridgeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AgentBridgeException(MvpErrorCode.ORCHESTRATION_PLAN_INVALID, "Invalid model JSON", ex);
        }
    }

    private String extractJson(String content) {
        String trimmed = nullToEmpty(content).trim();
        if (trimmed.startsWith("```")) {
            int firstNl = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNl > 0 && lastFence > firstNl) {
                trimmed = trimmed.substring(firstNl + 1, lastFence).trim();
            }
        }
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    private String candidatesJson(List<AgentCapabilitySummary> candidates) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (candidates != null) {
            for (AgentCapabilitySummary candidate : candidates) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("agentId", candidate.agentId());
                row.put("agentVersion", candidate.agentVersion());
                row.put("name", candidate.name());
                row.put("description", candidate.description());
                rows.add(row);
            }
        }
        try {
            return objectMapper.writeValueAsString(rows);
        } catch (Exception ex) {
            return "[]";
        }
    }

    private String mapJson(Map<String, String> map) {
        try {
            return objectMapper.writeValueAsString(map == null ? Map.of() : map);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isTextual() ? value.asText() : null;
    }

    private String trimTrailingSlash(String value) {
        String result = value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private AgentBridgeException failed(String message) {
        return new AgentBridgeException(MvpErrorCode.INTERNAL_ERROR, message);
    }
}
