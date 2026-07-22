package com.jd.genie.platform.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.model.response.GptProcessResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContractSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void apiResponseSuccessSerialization() throws Exception {
        ApiResponse<Map<String, String>> response =
            new ApiResponse<>("OK", "success", Map.of("id", "uuid"));

        String json = objectMapper.writeValueAsString(response);
        JsonNode node = objectMapper.readTree(json);

        assertEquals("OK", node.get("code").asText());
        assertEquals("success", node.get("message").asText());
        assertEquals("uuid", node.get("data").get("id").asText());
    }

    @Test
    void apiResponseNullDataRetainsField() throws Exception {
        ApiResponse<Void> response = new ApiResponse<>("CONVERSATION_BUSY", "busy", null);

        String json = objectMapper.writeValueAsString(response);
        JsonNode node = objectMapper.readTree(json);

        assertTrue(node.has("data"));
        assertTrue(node.get("data").isNull());
    }

    @Test
    void pageResponseCamelCase() throws Exception {
        PageResponse<String> page = new PageResponse<>(List.of("a"), 1, 20, false);

        String json = objectMapper.writeValueAsString(page);
        JsonNode node = objectMapper.readTree(json);

        assertTrue(node.has("pageSize"));
        assertTrue(node.has("hasMore"));
        assertEquals(20, node.get("pageSize").asInt());
    }

    @Test
    void streamSnapshotEnvelopeFields() throws Exception {
        GptProcessResult event = GptProcessResult.builder()
            .status("success")
            .response("hello")
            .responseAll("hello")
            .finished(true)
            .responseType("markdown")
            .packageType("result")
            .build();

        StreamSnapshotEnvelope envelope = new StreamSnapshotEnvelope(1, false, List.of(event));
        String json = objectMapper.writeValueAsString(envelope);
        JsonNode node = objectMapper.readTree(json);

        assertEquals(1, node.get("payloadVersion").asInt());
        assertFalse(node.get("truncated").asBoolean());
        assertNotNull(node.get("events"));
        assertEquals(1, node.get("events").size());
    }

    @Test
    void gptProcessResultEventsCanEnterSnapshot() throws Exception {
        GptProcessResult event = GptProcessResult.builder()
            .status("running")
            .response("partial")
            .responseAll("partial")
            .finished(false)
            .useTimes(1L)
            .useTokens(10L)
            .resultMap(Map.of("agentType", "5"))
            .responseType("markdown")
            .traceId("trace-1")
            .reqId("req-1")
            .encrypted(false)
            .packageType("result")
            .build();

        StreamSnapshotEnvelope envelope = new StreamSnapshotEnvelope(1, false, List.of(event));
        String json = objectMapper.writeValueAsString(envelope);

        assertFalse(json.contains("heartbeat"));
        assertTrue(json.contains("partial"));
    }
}
