package com.jd.genie.platform.conversation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiConversationTitleModelPortTest {
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    @Test
    void returnsModelContentAndDisablesThinking() throws Exception {
        AtomicReference<String> captured = new AtomicReference<>();
        OpenAiConversationTitleModelPort port = port(captured, """
                {"choices":[{"message":{"content":"东南亚车市"}}]}
                """);

        assertEquals("东南亚车市", port.summarizeFirstQuery("国产新能源汽车东南亚市场"));
        String body = captured.get();
        assertTrue(body.contains("\"model\":\"qwen3.7-max\""));
        assertTrue(body.contains("\"enable_thinking\":false"));
        assertTrue(body.contains("根据第一句提问做语义概括"));
        assertTrue(body.contains("可以少于9个字"));
        assertTrue(body.contains("最多9个字"));
        assertTrue(body.contains("只输出标题"));
        assertTrue(body.contains("国产新能源汽车东南亚市场"));
    }

    @Test
    void usesReasoningContentWhenMessageContentIsEmpty() {
        OpenAiConversationTitleModelPort port = port(null, """
                {"choices":[{"message":{"content":"","reasoning_content":"新能源车市"}}]}
                """);
        assertEquals("新能源车市", port.summarizeFirstQuery("国产新能源汽车东南亚市场"));
    }

    @Test
    void returnsEmptyWhenHttpFails() {
        OpenAiConversationTitleModelPort port = port(null, 500, "{\"error\":\"nope\"}");
        assertEquals("", port.summarizeFirstQuery("任意提问"));
    }

    @Test
    void retriesDefaultModelAfterTitleModelHttpFailure() {
        List<String> models = new ArrayList<>();
        Interceptor interceptor = chain -> {
            models.add(modelName(chain.request()));
            int code = models.size() == 1 ? 403 : 200;
            String body = models.size() == 1
                    ? "{\"error\":{\"message\":\"Access denied by API-Key restrictions.\"}}"
                    : "{\"choices\":[{\"message\":{\"content\":\"东南亚车市\"}}]}";
            return response(chain.request(), code, body);
        };
        OpenAiConversationTitleModelPort port = new OpenAiConversationTitleModelPort(
                "qwen3.7-flash",
                "qwen3.7-max",
                "https://title.test",
                "test-key",
                "/chat/completions",
                new ObjectMapper(),
                new OkHttpClient.Builder().addInterceptor(interceptor).build()
        );

        assertEquals("东南亚车市", port.summarizeFirstQuery("国产新能源汽车东南亚市场"));
        assertEquals(List.of("qwen3.7-flash", "qwen3.7-max"), models);
    }

    private OpenAiConversationTitleModelPort port(AtomicReference<String> captured, String body) {
        return port(captured, 200, body);
    }

    private OpenAiConversationTitleModelPort port(AtomicReference<String> captured, int code, String body) {
        Interceptor interceptor = chain -> {
            if (captured != null && chain.request().body() != null) {
                okio.Buffer buffer = new okio.Buffer();
                chain.request().body().writeTo(buffer);
                captured.set(buffer.readUtf8());
            }
            return response(chain.request(), code, body);
        };
        return new OpenAiConversationTitleModelPort(
                "qwen3.7-max",
                "https://title.test",
                "test-key",
                "/chat/completions",
                new ObjectMapper(),
                new OkHttpClient.Builder().addInterceptor(interceptor).build()
        );
    }

    private static Response response(Request request, int code, String body) {
        return new Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("ok")
                .body(ResponseBody.create(body, JSON))
                .build();
    }

    private static String modelName(Request request) {
        try {
            okio.Buffer buffer = new okio.Buffer();
            request.body().writeTo(buffer);
            return new ObjectMapper().readTree(buffer.readUtf8()).path("model").asText();
        } catch (Exception ex) {
            return "";
        }
    }
}
