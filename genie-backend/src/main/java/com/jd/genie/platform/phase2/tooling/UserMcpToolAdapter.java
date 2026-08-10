package com.jd.genie.platform.phase2.tooling;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.agent.tool.BaseTool;
import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2contract.error.Phase2ContractException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UserMcpToolAdapter implements BaseTool {
    private static final int MAX_ARGUMENT_BYTES = 256 * 1024;
    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
    private final JdbcTemplate jdbc;
    private final McpClientAdapter client;
    private final CredentialEnvelopeService credentials;
    private final McpUrlPolicy urlPolicy;
    private final ObjectMapper mapper;
    private final CurrentUser user;
    private final String toolId;
    private final String serverId;
    private final String capabilityKey;
    private final String runtimeName;
    private final String remoteToolName;
    private final String description;
    private final JsonNode schema;

    public UserMcpToolAdapter(
            JdbcTemplate jdbc,
            McpClientAdapter client,
            CredentialEnvelopeService credentials,
            McpUrlPolicy urlPolicy,
            ObjectMapper mapper,
            CurrentUser user,
            String toolId,
            String serverId,
            String capabilityKey,
            String runtimeName,
            String remoteToolName,
            String description,
            JsonNode schema
    ) {
        this.jdbc = jdbc;
        this.client = client;
        this.credentials = credentials;
        this.urlPolicy = urlPolicy;
        this.mapper = mapper;
        this.user = user;
        this.toolId = toolId;
        this.serverId = serverId;
        this.capabilityKey = capabilityKey;
        this.runtimeName = runtimeName;
        this.remoteToolName = remoteToolName;
        this.description = description;
        this.schema = schema;
    }

    @Override
    public String getName() {
        return runtimeName;
    }

    @Override
    public String getDescription() {
        return description == null ? "" : description;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> toParams() {
        return mapper.convertValue(schema, Map.class);
    }

    @Override
    public Object execute(Object input) {
        if (!(input instanceof Map<?, ?> raw)) {
            throw invalidInput();
        }
        Map<String, Object> arguments = new LinkedHashMap<>();
        raw.forEach((key, value) -> {
            if (!(key instanceof String)) {
                throw invalidInput();
            }
            arguments.put((String) key, value);
        });
        try {
            if (mapper.writeValueAsBytes(arguments).length > MAX_ARGUMENT_BYTES) {
                throw invalidInput();
            }
            validateSchema(arguments);
            Map<String, Object> current = jdbc.query(
                    "SELECT s.server_url,s.auth_type,s.auth_name,s.credential_envelope,s.status,s.deleted_at,t.enabled,t.available,t.tool_name FROM mcp_tool t JOIN mcp_server s ON s.id=t.mcp_server_id WHERE t.id=? AND t.mcp_server_id=? AND t.tenant_id=? AND t.owner_id=?",
                    rs -> rs.next()
                            ? Map.of(
                            "url", rs.getString(1),
                            "auth", rs.getString(2),
                            "name", rs.getString(3) == null ? "" : rs.getString(3),
                            "envelope", rs.getString(4) == null ? "" : rs.getString(4),
                            "status", rs.getString(5),
                            "deleted", rs.getObject(6) == null ? Boolean.FALSE : Boolean.TRUE,
                            "enabled", rs.getBoolean(7),
                            "available", rs.getBoolean(8),
                            "toolName", rs.getString(9)
                    )
                            : null,
                    toolId,
                    serverId,
                    user.tenantId(),
                    user.userId()
            );
            if (current == null
                    || !"ENABLED".equals(current.get("status"))
                    || Boolean.TRUE.equals(current.get("deleted"))
                    || !Boolean.TRUE.equals(current.get("enabled"))
                    || !Boolean.TRUE.equals(current.get("available"))) {
                throw notBound();
            }
            String url = (String) current.get("url");
            urlPolicy.validate(url);
            String secret = credentials.decrypt(
                    blankToNull((String) current.get("envelope")),
                    user.tenantId(),
                    user.userId(),
                    serverId,
                    AuthType.valueOf((String) current.get("auth"))
            );
            String mcpToolName = firstNonBlank((String) current.get("toolName"), remoteToolName);
            if (mcpToolName == null || mcpToolName.isBlank()) {
                throw new Phase2ContractException(MvpErrorCode.TOOL_INVALID_RESPONSE, "mcp tool name missing");
            }
            normalizeTicketArguments(arguments, mcpToolName);
            JsonNode response;
            try {
                response = client.callTool(
                        url,
                        AuthType.valueOf((String) current.get("auth")),
                        blankToNull((String) current.get("name")),
                        secret,
                        mcpToolName,
                        arguments
                );
            } finally {
                secret = null;
            }
            if (response == null || mapper.writeValueAsBytes(response).length > MAX_RESPONSE_BYTES) {
                throw new Phase2ContractException(MvpErrorCode.TOOL_INVALID_RESPONSE, "invalid tool response");
            }
            // BaseAgent expects a String tool result for the LLM observation.
            return flattenMcpResult(response, mcpToolName);
        } catch (Phase2ContractException ex) {
            throw ex;
        } catch (ToolCapabilityException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new Phase2ContractException(MvpErrorCode.TOOL_INVALID_RESPONSE, "tool execution failed", ex);
        }
    }

    private void validateSchema(Map<String, Object> arguments) {
        if (schema == null || !schema.isObject()) {
            throw invalidInput();
        }
        JsonNode required = schema.get("required");
        if (required != null && required.isArray()) {
            for (JsonNode key : required) {
                if (!arguments.containsKey(key.asText())) {
                    throw invalidInput();
                }
            }
        }
        JsonNode properties = schema.get("properties");
        if (properties != null && properties.isObject()) {
            for (String key : arguments.keySet()) {
                JsonNode property = properties.get(key);
                if (property == null) {
                    continue;
                }
                String type = property.path("type").asText("");
                Object value = arguments.get(key);
                if (!matches(type, value)) {
                    throw invalidInput();
                }
            }
        }
    }

    private boolean matches(String type, Object value) {
        return switch (type) {
            case "string" -> value instanceof String;
            case "number" -> value instanceof Number;
            case "integer" -> value instanceof Integer || value instanceof Long;
            case "boolean" -> value instanceof Boolean;
            case "object" -> value instanceof Map;
            case "array" -> value instanceof List;
            default -> true;
        };
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }

    private static final int MAX_RESULT_CHARS = 3500;
    private static final int MAX_RESULT_LINES = 30;
    private static final int MAX_TICKET_LIMIT = 60;
    private static final int MAX_SCHEDULE_ROWS = 60;
    private static final Pattern TRAIN_DETAIL = Pattern.compile(
            "^\\s*([GDCZTKOFS]\\d{1,5})\\s+(.+?)\\(telecode:\\w+\\)\\s*->\\s*(.+?)\\(telecode:\\w+\\)\\s+"
                    + "(\\d{1,2}:\\d{2})\\s*->\\s*(\\d{1,2}:\\d{2})"
    );
    private static final Pattern TRAIN_CODE = Pattern.compile("^\\s*([GDCZTKOFS]\\d{1,5})\\b");
    private static final Pattern CLOCK = Pattern.compile("\\b(\\d{1,2}:\\d{2})\\b");

    private void normalizeTicketArguments(Map<String, Object> arguments, String toolName) {
        if (!isTicketQueryTool(toolName)) {
            return;
        }
        Object limited = arguments.get("limitedNum");
        int n = 0;
        if (limited instanceof Number number) {
            n = number.intValue();
        }
        if (n <= 0 || n > MAX_TICKET_LIMIT) {
            arguments.put("limitedNum", MAX_TICKET_LIMIT);
        }
        Object format = arguments.get("format");
        if (!(format instanceof String formatText) || formatText.isBlank()) {
            arguments.put("format", "text");
        }
        Object sortFlag = arguments.get("sortFlag");
        if (!(sortFlag instanceof String sortText) || sortText.isBlank()) {
            arguments.put("sortFlag", "startTime");
        }
    }

    private static boolean isTicketQueryTool(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return false;
        }
        String normalized = toolName.toLowerCase().replace('_', '-');
        return normalized.contains("get-tickets") || normalized.contains("get-interline-tickets");
    }

    private String flattenMcpResult(JsonNode response, String toolName) {
        try {
            String text = extractTextContent(response);
            if (isTicketQueryTool(toolName)) {
                return summarizeTicketResult(text);
            }
            return truncateForLlm(text);
        } catch (Phase2ContractException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new Phase2ContractException(MvpErrorCode.TOOL_INVALID_RESPONSE, "invalid tool response", ex);
        }
    }

    private String extractTextContent(JsonNode response) throws Exception {
        JsonNode content = response.path("content");
        if (content.isArray() && !content.isEmpty()) {
            StringBuilder text = new StringBuilder();
            for (JsonNode item : content) {
                if ("text".equals(item.path("type").asText()) && item.path("text").isTextual()) {
                    if (!text.isEmpty()) {
                        text.append('\n');
                    }
                    text.append(item.path("text").asText());
                }
            }
            if (!text.isEmpty()) {
                return text.toString();
            }
        }
        if (response.isTextual()) {
            return response.asText();
        }
        return mapper.writeValueAsString(response);
    }

    /**
     * Compress 12306 timetable dumps into a compact per-train schedule for the LLM.
     * Includes departure times so "list every train" questions can finish without re-querying.
     */
    static String summarizeTicketResult(String text) {
        if (text == null || text.isBlank()) {
            return "票务查询结果为空。请直接用 SUCCESS JSON 回答用户（说明未查到班次）。";
        }
        Map<String, ScheduleRow> byTrain = new LinkedHashMap<>();
        for (String line : text.split("\n", -1)) {
            ScheduleRow row = parseScheduleRow(line);
            if (row == null) {
                continue;
            }
            // Keep first occurrence per train code (dedupe multi-destination duplicates).
            byTrain.putIfAbsent(row.code, row);
            if (byTrain.size() >= MAX_SCHEDULE_ROWS) {
                break;
            }
        }
        if (byTrain.isEmpty()) {
            return truncateForLlm(text);
        }
        List<ScheduleRow> rows = new ArrayList<>(byTrain.values());
        rows.sort(Comparator
                .comparing((ScheduleRow r) -> normalizeClock(r.depart))
                .thenComparing(r -> r.code));

        StringBuilder summary = new StringBuilder();
        summary.append("票务查询摘要（已压缩；请立即用 SUCCESS JSON 回答，禁止再次全量查询）\n");
        summary.append("uniqueTrainCount=").append(rows.size()).append('\n');
        summary.append("scheduleFormat=train|depart|arrive|from->to\n");
        summary.append("schedule:\n");
        for (ScheduleRow row : rows) {
            summary.append(row.code).append('|')
                    .append(row.depart).append('|')
                    .append(row.arrive).append('|')
                    .append(row.from).append("->").append(row.to)
                    .append('\n');
        }
        summary.append("指令: 若用户要班次数量/出发时间/列表，直接把 schedule 写入 SUCCESS output；勿再调票务工具。\n");
        return summary.toString();
    }

    private static ScheduleRow parseScheduleRow(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }
        Matcher detail = TRAIN_DETAIL.matcher(line);
        if (detail.find()) {
            return new ScheduleRow(
                    detail.group(1),
                    detail.group(4),
                    detail.group(5),
                    detail.group(2).trim(),
                    detail.group(3).trim()
            );
        }
        Matcher codeMatcher = TRAIN_CODE.matcher(line);
        if (!codeMatcher.find()) {
            return null;
        }
        Matcher clocks = CLOCK.matcher(line);
        String depart = null;
        String arrive = null;
        if (clocks.find()) {
            depart = clocks.group(1);
        }
        if (clocks.find()) {
            arrive = clocks.group(1);
        }
        if (depart == null) {
            return null;
        }
        return new ScheduleRow(codeMatcher.group(1), depart, arrive == null ? "?" : arrive, "?", "?");
    }

    private static String normalizeClock(String clock) {
        if (clock == null || !clock.contains(":")) {
            return "99:99";
        }
        String[] parts = clock.split(":", 2);
        try {
            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);
            return String.format("%02d:%02d", hour, minute);
        } catch (Exception ignored) {
            return clock;
        }
    }

    private record ScheduleRow(String code, String depart, String arrive, String from, String to) {
    }

    static String truncateForLlm(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String[] lines = text.split("\n", -1);
        if (lines.length > MAX_RESULT_LINES) {
            StringBuilder clipped = new StringBuilder();
            for (int i = 0; i < MAX_RESULT_LINES; i++) {
                if (i > 0) {
                    clipped.append('\n');
                }
                clipped.append(lines[i]);
            }
            clipped.append("\n...(truncated: showing ")
                    .append(MAX_RESULT_LINES)
                    .append(" of ")
                    .append(lines.length)
                    .append(" lines; answer from this sample — do not re-query for the full list)");
            text = clipped.toString();
        }
        if (text.length() <= MAX_RESULT_CHARS) {
            return text;
        }
        return text.substring(0, MAX_RESULT_CHARS)
                + "\n...(truncated; answer from this sample — do not re-query)";
    }

    private static Phase2ContractException invalidInput() {
        return new Phase2ContractException(MvpErrorCode.TOOL_INVALID_INPUT, "tool input invalid");
    }

    private static ToolCapabilityException notBound() {
        return new ToolCapabilityException("tool is not bound");
    }
}
