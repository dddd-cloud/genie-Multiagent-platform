package com.jd.genie.platform.phase2.runtime.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.platform.agentbridge.AgentBridgeException;
import com.jd.genie.platform.contract.MvpErrorCode;

import java.util.Set;

public final class AgentTaskResultParser {
    private static final int MAX_OUTPUT_LENGTH = 12_000;
    private static final Set<String> FAILURE_ERROR_CODES = Set.of(
            "INVALID_INPUT",
            "AGENT_OFFLINE",
            "TOOL_PERMISSION_DENIED",
            "TOOL_TIMEOUT",
            "TOOL_UNAVAILABLE",
            "TOOL_INVALID_RESPONSE",
            "AGENT_INVALID_RESULT",
            "CONTEXT_BUDGET_EXCEEDED",
            "EXECUTION_ERROR",
            "CANCELLED"
    );
    private final ObjectMapper objectMapper;

    public AgentTaskResultParser() {
        this(new ObjectMapper());
    }

    AgentTaskResultParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AgentTaskResult parse(String raw) {
        try {
            JsonNode root = objectMapper.readTree(extractJsonObject(raw));
            if (root == null || !root.isObject() || root.size() != 4
                    || !root.has("status") || !root.has("output")
                    || !root.has("errorCode") || !root.has("retryable")) {
                throw invalid();
            }
            String status = root.path("status").asText(null);
            if ("SUCCESS".equals(status) && root.path("output").isTextual()
                    && !root.path("output").asText().isBlank()
                    && root.path("output").asText().length() <= MAX_OUTPUT_LENGTH
                    && root.path("errorCode").isNull()
                    && root.path("retryable").isBoolean()
                    && !root.path("retryable").asBoolean()) {
                return AgentTaskResult.success(root.path("output").asText());
            }
            if ("FAILURE".equals(status) && root.path("output").isNull()
                    && root.path("errorCode").isTextual()
                    && FAILURE_ERROR_CODES.contains(root.path("errorCode").asText())
                    && root.path("retryable").isBoolean()) {
                return AgentTaskResult.failure(root.path("errorCode").asText(), root.path("retryable").asBoolean());
            }
            throw invalid();
        } catch (AgentBridgeException error) {
            throw error;
        } catch (Exception error) {
            throw invalid();
        }
    }

    private String extractJsonObject(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
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

    private AgentBridgeException invalid() {
        return new AgentBridgeException(MvpErrorCode.AGENT_INVALID_RESULT, "Agent result must be one JSON object");
    }
}
