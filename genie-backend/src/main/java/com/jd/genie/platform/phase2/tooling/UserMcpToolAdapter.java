package com.jd.genie.platform.phase2.tooling;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.agent.tool.BaseTool;
import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2contract.error.Phase2ContractException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    private final String description;
    private final JsonNode schema;

    public UserMcpToolAdapter(JdbcTemplate jdbc, McpClientAdapter client, CredentialEnvelopeService credentials,
                              McpUrlPolicy urlPolicy, ObjectMapper mapper, CurrentUser user, String toolId,
                              String serverId, String capabilityKey, String runtimeName, String description, JsonNode schema) {
        this.jdbc = jdbc; this.client = client; this.credentials = credentials; this.urlPolicy = urlPolicy; this.mapper = mapper;
        this.user = user; this.toolId = toolId; this.serverId = serverId; this.capabilityKey = capabilityKey; this.runtimeName = runtimeName; this.description = description; this.schema = schema;
    }
    @Override public String getName() { return runtimeName; }
    @Override public String getDescription() { return description == null ? "" : description; }
    @Override public Map<String, Object> toParams() { return mapper.convertValue(schema, Map.class); }

    @Override
    public Object execute(Object input) {
        if (!(input instanceof Map<?, ?> raw)) throw invalidInput();
        Map<String, Object> arguments = new LinkedHashMap<>();
        raw.forEach((key, value) -> { if (!(key instanceof String)) throw invalidInput(); arguments.put((String) key, value); });
        try {
            if (mapper.writeValueAsBytes(arguments).length > MAX_ARGUMENT_BYTES) throw invalidInput();
            validateSchema(arguments);
            Map<String, Object> current = jdbc.query("SELECT s.server_url,s.auth_type,s.auth_name,s.credential_envelope,s.status,s.deleted_at,t.enabled,t.available FROM mcp_tool t JOIN mcp_server s ON s.id=t.mcp_server_id WHERE t.id=? AND t.mcp_server_id=? AND t.tenant_id=? AND t.owner_id=?", rs -> rs.next() ? Map.of("url", rs.getString(1), "auth", rs.getString(2), "name", rs.getString(3), "envelope", rs.getString(4), "status", rs.getString(5), "deleted", rs.getObject(6), "enabled", rs.getBoolean(7), "available", rs.getBoolean(8)) : null, toolId, serverId, user.tenantId(), user.userId());
            if (current == null || !"ENABLED".equals(current.get("status")) || current.get("deleted") != null || !Boolean.TRUE.equals(current.get("enabled")) || !Boolean.TRUE.equals(current.get("available"))) throw notBound();
            String url = (String) current.get("url"); urlPolicy.validate(url);
            String secret = credentials.decrypt((String) current.get("envelope"), user.tenantId(), user.userId(), serverId, AuthType.valueOf((String) current.get("auth")));
            JsonNode response;
            try { response = client.callTool(url, AuthType.valueOf((String) current.get("auth")), (String) current.get("name"), secret, toolId, arguments); }
            finally { secret = null; }
            if (response == null || mapper.writeValueAsBytes(response).length > MAX_RESPONSE_BYTES) throw new Phase2ContractException(MvpErrorCode.TOOL_INVALID_RESPONSE, "invalid tool response");
            return response;
        } catch (Phase2ContractException ex) { throw ex; }
        catch (ToolCapabilityException ex) { throw ex; }
        catch (Exception ex) { throw new Phase2ContractException(MvpErrorCode.TOOL_INVALID_RESPONSE, "tool execution failed", ex); }
    }

    private void validateSchema(Map<String, Object> arguments) throws Exception {
        if (schema == null || !schema.isObject()) throw invalidInput();
        JsonNode required = schema.get("required"); if (required != null && required.isArray()) for (JsonNode key : required) if (!arguments.containsKey(key.asText())) throw invalidInput();
        JsonNode properties = schema.get("properties"); if (properties != null && properties.isObject()) for (String key : arguments.keySet()) { JsonNode property = properties.get(key); if (property == null) continue; String type = property.path("type").asText(""); Object value = arguments.get(key); if (!matches(type, value)) throw invalidInput(); }
    }
    private boolean matches(String type, Object value) { return switch (type) { case "string" -> value instanceof String; case "number" -> value instanceof Number; case "integer" -> value instanceof Integer || value instanceof Long; case "boolean" -> value instanceof Boolean; case "object" -> value instanceof Map; case "array" -> value instanceof List; default -> true; }; }
    private static Phase2ContractException invalidInput() { return new Phase2ContractException(MvpErrorCode.TOOL_INVALID_INPUT, "tool input invalid"); }
    private static ToolCapabilityException notBound() { return new ToolCapabilityException("tool is not bound"); }
}
