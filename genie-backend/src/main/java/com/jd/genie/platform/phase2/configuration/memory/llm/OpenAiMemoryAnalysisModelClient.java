package com.jd.genie.platform.phase2.configuration.memory.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.agent.llm.LLMSettings;
import com.jd.genie.agent.llm.LlmSettingsResolver;
import com.jd.genie.config.GenieConfig;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2.configuration.memory.exception.MemoryAnalysisException;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class OpenAiMemoryAnalysisModelClient implements MemoryAnalysisModelClient {
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final String DEFAULT_INTERFACE_URL = "/chat/completions";
    private static final int DEFAULT_MAX_TOKENS = 1024;

    private final GenieConfig genieConfig;
    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient;

    @Autowired
    public OpenAiMemoryAnalysisModelClient(GenieConfig genieConfig) {
        this(genieConfig, new ObjectMapper(), new OkHttpClient());
    }

    OpenAiMemoryAnalysisModelClient(GenieConfig genieConfig, ObjectMapper objectMapper, OkHttpClient httpClient) {
        this.genieConfig = genieConfig;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    @Override
    public MemoryAnalysisModelResponse analyzeMemory(MemoryAnalysisModelRequest request) {
        return call(request);
    }

    @Override
    public MemoryAnalysisModelResponse summarizeConversation(MemoryAnalysisModelRequest request) {
        return call(request);
    }

    private MemoryAnalysisModelResponse call(MemoryAnalysisModelRequest request) {
        LLMSettings settings = resolveSettings();
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                "model", settings.getModel() == null || settings.getModel().isBlank() ? genieConfig.getReactModelName() : settings.getModel(),
                "stream", false,
                "temperature", 0,
                "max_tokens", settings.getMaxTokens() > 0 ? settings.getMaxTokens() : DEFAULT_MAX_TOKENS,
                "messages", List.of(
                    Map.of("role", "system", "content", request.systemPrompt()),
                    Map.of("role", "user", "content", request.userPrompt())
                )
            ));
            Request httpRequest = new Request.Builder()
                .url(endpoint(settings))
                .header("Authorization", "Bearer " + settings.getApiKey())
                .header("Content-Type", "application/json")
                .post(RequestBody.create(body, JSON))
                .build();
            OkHttpClient client = httpClient.newBuilder()
                .callTimeout(Duration.ofMillis(request.timeoutMs()))
                .readTimeout(Duration.ofMillis(request.timeoutMs()))
                .build();
            try (Response response = client.newCall(httpRequest).execute()) {
                if (!response.isSuccessful()) {
                    throw failed();
                }
                ResponseBody responseBody = response.body();
                if (responseBody == null) {
                    throw failed();
                }
                JsonNode root = objectMapper.readTree(responseBody.string());
                JsonNode content = root.path("choices").path(0).path("message").path("content");
                if (!content.isTextual() || content.asText().isBlank()) {
                    throw failed();
                }
                return new MemoryAnalysisModelResponse(content.asText());
            }
        } catch (MemoryAnalysisException ex) {
            throw ex;
        } catch (Exception ex) {
            throw failed();
        }
    }

    private LLMSettings resolveSettings() {
        LLMSettings settings = LlmSettingsResolver.resolveComplete(genieConfig);
        if (settings == null) {
            throw failed();
        }
        return settings;
    }

    private String endpoint(LLMSettings settings) {
        String baseUrl = trimTrailingSlash(settings.getBaseUrl());
        String interfaceUrl = settings.getInterfaceUrl();
        if (interfaceUrl == null || interfaceUrl.isBlank()) {
            interfaceUrl = DEFAULT_INTERFACE_URL;
        }
        if (!interfaceUrl.startsWith("/")) {
            interfaceUrl = "/" + interfaceUrl;
        }
        return baseUrl + interfaceUrl;
    }

    private String trimTrailingSlash(String value) {
        String result = value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private MemoryAnalysisException failed() {
        return new MemoryAnalysisException(MvpErrorCode.MEMORY_ANALYSIS_FAILED, MvpErrorCode.MEMORY_ANALYSIS_FAILED.name());
    }
}
