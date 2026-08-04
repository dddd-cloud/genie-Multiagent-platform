package com.jd.genie.platform.phase2.tooling;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;

public interface McpClientAdapter {
    List<RemoteTool> listTools(String serverUrl, AuthType authType, String authName, String credential);
    JsonNode callTool(String serverUrl, AuthType authType, String authName, String credential, String toolName, Map<String,Object> arguments);
    record RemoteTool(String name, String description, JsonNode inputSchema) { }
}
