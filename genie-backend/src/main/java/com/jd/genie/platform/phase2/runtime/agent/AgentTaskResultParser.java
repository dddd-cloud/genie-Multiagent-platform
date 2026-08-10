package com.jd.genie.platform.phase2.runtime.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.platform.agentbridge.AgentBridgeException;
import com.jd.genie.platform.contract.MvpErrorCode;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the frozen Phase2 configured-agent result contract.
 * Strict JSON first; if the model leaves unescaped quotes/newlines inside
 * {@code output}, recover the envelope by anchored prefix/suffix matching.
 */
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

    private static final Pattern SUCCESS_PREFIX = Pattern.compile(
            "^\\s*\\{\\s*\"status\"\\s*:\\s*\"SUCCESS\"\\s*,\\s*\"output\"\\s*:\\s*\"",
            Pattern.DOTALL
    );
    private static final Pattern SUCCESS_SUFFIX = Pattern.compile(
            "\"\\s*,\\s*\"errorCode\"\\s*:\\s*null\\s*,\\s*\"retryable\"\\s*:\\s*false\\s*}\\s*$",
            Pattern.DOTALL
    );
    private static final Pattern FAILURE_PATTERN = Pattern.compile(
            "^\\s*\\{\\s*\"status\"\\s*:\\s*\"FAILURE\"\\s*,\\s*\"output\"\\s*:\\s*null\\s*,\\s*"
                    + "\"errorCode\"\\s*:\\s*\"([A-Z0-9_]+)\"\\s*,\\s*"
                    + "\"retryable\"\\s*:\\s*(true|false)\\s*}\\s*$",
            Pattern.DOTALL
    );

    private final ObjectMapper objectMapper;

    public AgentTaskResultParser() {
        this(new ObjectMapper());
    }

    AgentTaskResultParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AgentTaskResult parse(String raw) {
        String candidate = extractJsonObject(raw);
        AgentTaskResult strict = tryParseStrict(candidate);
        if (strict != null) {
            return strict;
        }
        AgentTaskResult recovered = tryRecoverContract(candidate);
        if (recovered != null) {
            return recovered;
        }
        throw invalid();
    }

    private AgentTaskResult tryParseStrict(String candidate) {
        try {
            JsonNode root = objectMapper.readTree(candidate);
            if (root == null || !root.isObject() || root.size() != 4
                    || !root.has("status") || !root.has("output")
                    || !root.has("errorCode") || !root.has("retryable")) {
                return null;
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
                return AgentTaskResult.failure(
                        root.path("errorCode").asText(),
                        root.path("retryable").asBoolean()
                );
            }
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private AgentTaskResult tryRecoverContract(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return null;
        }
        AgentTaskResult success = recoverSuccess(candidate);
        if (success != null) {
            return success;
        }
        return recoverFailure(candidate);
    }

    private AgentTaskResult recoverSuccess(String candidate) {
        Matcher prefix = SUCCESS_PREFIX.matcher(candidate);
        if (!prefix.find()) {
            return null;
        }
        Matcher suffix = SUCCESS_SUFFIX.matcher(candidate);
        if (!suffix.find() || suffix.start() < prefix.end()) {
            return null;
        }
        // Reject unexpected trailing/leading junk around the 4-field envelope.
        if (prefix.start() != 0 && !candidate.substring(0, prefix.start()).isBlank()) {
            return null;
        }
        String output = unescapeJsonString(candidate.substring(prefix.end(), suffix.start()));
        if (output.isBlank() || output.length() > MAX_OUTPUT_LENGTH) {
            return null;
        }
        return AgentTaskResult.success(output);
    }

    private AgentTaskResult recoverFailure(String candidate) {
        Matcher matcher = FAILURE_PATTERN.matcher(candidate);
        if (!matcher.matches()) {
            return null;
        }
        String errorCode = matcher.group(1);
        if (!FAILURE_ERROR_CODES.contains(errorCode)) {
            return null;
        }
        return AgentTaskResult.failure(errorCode, Boolean.parseBoolean(matcher.group(2)));
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

    /**
     * Decodes JSON string escapes when present; keeps literal unescaped quotes/newlines as-is.
     */
    static String unescapeJsonString(String rawSlice) {
        if (rawSlice == null || rawSlice.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(rawSlice.length());
        for (int i = 0; i < rawSlice.length(); i++) {
            char c = rawSlice.charAt(i);
            if (c != '\\' || i + 1 >= rawSlice.length()) {
                sb.append(c);
                continue;
            }
            char next = rawSlice.charAt(++i);
            switch (next) {
                case 'n' -> sb.append('\n');
                case 'r' -> sb.append('\r');
                case 't' -> sb.append('\t');
                case '"' -> sb.append('"');
                case '\\' -> sb.append('\\');
                case '/' -> sb.append('/');
                case 'b' -> sb.append('\b');
                case 'f' -> sb.append('\f');
                case 'u' -> {
                    if (i + 4 < rawSlice.length()) {
                        String hex = rawSlice.substring(i + 1, i + 5);
                        try {
                            sb.append((char) Integer.parseInt(hex, 16));
                            i += 4;
                        } catch (NumberFormatException ignored) {
                            sb.append('\\').append('u');
                        }
                    } else {
                        sb.append('\\').append('u');
                    }
                }
                default -> sb.append('\\').append(next);
            }
        }
        return sb.toString();
    }

    private AgentBridgeException invalid() {
        return new AgentBridgeException(MvpErrorCode.AGENT_INVALID_RESULT, "Agent result must be one JSON object");
    }
}
