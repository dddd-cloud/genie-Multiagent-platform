package com.jd.genie.platform.conversation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI-compatible title call. Uses {@code GENIE_TITLE_MODEL} when set,
 * otherwise {@code DEFAULT_MODEL} (qwen3.7-max). Retries {@code DEFAULT_MODEL}
 * if the primary call fails.
 */
@Slf4j
@Component
public class OpenAiConversationTitleModelPort implements ConversationTitleModelPort {
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final int QUERY_CHAR_LIMIT = 500;
    private static final int MAX_TOKENS = 64;
    private static final long TIMEOUT_MS = 8_000L;
    private static final String SYSTEM_PROMPT =
            "根据第一句提问做语义概括，可以少于9个字，最多9个字，只输出标题";

    private final String model;
    private final String fallbackModel;
    private final String endpoint;
    private final String apiKey;
    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient;

    @Autowired
    public OpenAiConversationTitleModelPort(
            @Value("${genie.conversation.title-model:${DEFAULT_MODEL:qwen3.7-max}}") String model,
            @Value("${DEFAULT_MODEL:qwen3.7-max}") String fallbackModel,
            @Value("${OPENAI_BASE_URL:}") String baseUrl,
            @Value("${OPENAI_API_KEY:}") String apiKey,
            @Value("${llm.default.interface_url:/chat/completions}") String interfaceUrl
    ) {
        this(model, fallbackModel, baseUrl, apiKey, interfaceUrl, new ObjectMapper(), new OkHttpClient());
    }

    OpenAiConversationTitleModelPort(
            String model,
            String baseUrl,
            String apiKey,
            String interfaceUrl,
            ObjectMapper objectMapper,
            OkHttpClient httpClient
    ) {
        this(model, "", baseUrl, apiKey, interfaceUrl, objectMapper, httpClient);
    }

    OpenAiConversationTitleModelPort(
            String model,
            String fallbackModel,
            String baseUrl,
            String apiKey,
            String interfaceUrl,
            ObjectMapper objectMapper,
            OkHttpClient httpClient
    ) {
        this.model = trimToEmpty(model);
        this.fallbackModel = trimToEmpty(fallbackModel);
        this.apiKey = trimToEmpty(apiKey);
        this.endpoint = joinEndpoint(baseUrl, interfaceUrl);
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    @Override
    public String summarizeFirstQuery(String query) {
        if (apiKey.isEmpty() || endpoint.isEmpty()) {
            return "";
        }
        String clipped = clipQuery(query);
        if (clipped.isEmpty()) {
            return "";
        }
        String primary = callModel(model, clipped);
        if (!primary.isBlank()) {
            return primary;
        }
        if (fallbackModel.isEmpty() || fallbackModel.equals(model)) {
            return "";
        }
        log.warn("Title LLM falling back to DEFAULT_MODEL after empty result model={}", model);
        return callModel(fallbackModel, clipped);
    }

    private String callModel(String modelName, String clipped) {
        if (modelName.isEmpty()) {
            return "";
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", modelName);
            body.put("stream", false);
            body.put("temperature", 0);
            body.put("max_tokens", MAX_TOKENS);
            body.put("enable_thinking", false);
            body.put("messages", List.of(
                    Map.of("role", "system", "content", SYSTEM_PROMPT),
                    Map.of("role", "user", "content", clipped)
            ));
            Request httpRequest = new Request.Builder()
                    .url(endpoint)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(objectMapper.writeValueAsString(body), JSON))
                    .build();
            OkHttpClient client = httpClient.newBuilder()
                    .callTimeout(Duration.ofMillis(TIMEOUT_MS))
                    .readTimeout(Duration.ofMillis(TIMEOUT_MS))
                    .connectTimeout(Duration.ofMillis(TIMEOUT_MS))
                    .build();
            try (Response response = client.newCall(httpRequest).execute()) {
                ResponseBody responseBody = response.body();
                String raw = responseBody == null ? "" : responseBody.string();
                if (!response.isSuccessful()) {
                    log.warn("Title LLM HTTP {} model={} err={}",
                            response.code(), modelName, errorSnippet(raw));
                    return "";
                }
                return extractTitleContent(raw);
            }
        } catch (Exception ex) {
            log.warn("Title LLM call failed model={}: {}", modelName, ex.getClass().getSimpleName());
            return "";
        }
    }

    private String extractTitleContent(String raw) {
        try {
            JsonNode message = objectMapper.readTree(raw).path("choices").path(0).path("message");
            String content = firstText(message, "content", "reasoning_content");
            return content == null ? "" : content.trim();
        } catch (Exception ex) {
            return "";
        }
    }

    private String errorSnippet(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        try {
            JsonNode error = objectMapper.readTree(raw).path("error");
            String message = text(error, "message");
            if (message.isBlank()) {
                message = text(error, "code");
            }
            if (!message.isBlank()) {
                return clipSnippet(message);
            }
        } catch (Exception ignored) {
            // Fall through to a raw snippet.
        }
        return clipSnippet(raw.replaceAll("\\s+", " "));
    }

    private static String clipSnippet(String value) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.length() <= 160 ? trimmed : trimmed.substring(0, 160);
    }

    private static String clipQuery(String query) {
        if (query == null) {
            return "";
        }
        String trimmed = query.replace('\n', ' ').replace('\r', ' ').replaceAll("\\s+", " ").trim();
        if (trimmed.length() <= QUERY_CHAR_LIMIT) {
            return trimmed;
        }
        return trimmed.substring(0, QUERY_CHAR_LIMIT);
    }

    private static String joinEndpoint(String baseUrl, String interfaceUrl) {
        String base = baseUrl == null ? "" : baseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String path = interfaceUrl == null || interfaceUrl.isBlank() ? "/chat/completions" : interfaceUrl.trim();
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return base.isEmpty() ? "" : base + path;
    }

    private static String firstText(JsonNode node, String... fields) {
        if (node == null) {
            return "";
        }
        for (String field : fields) {
            String value = text(node, field);
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.has(field) || !node.get(field).isTextual()) {
            return "";
        }
        return node.get(field).asText();
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
