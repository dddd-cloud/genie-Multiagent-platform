package com.jd.genie.platform.phase2.tooling;

import com.fasterxml.jackson.databind.JsonNode;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2contract.error.Phase2ContractException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
public class FakeMcpClientAdapter implements McpClientAdapter {
    public enum Scenario { SUCCESS, AUTH_FAIL, TIMEOUT, OVERSIZED, INVALID_SCHEMA, DUPLICATE_TOOL, DNS_REBINDING, CONNECTION_FAIL }
    private final Map<String, List<RemoteTool>> fixtures = new ConcurrentHashMap<>();
    private final Map<String, Scenario> scenarios = new ConcurrentHashMap<>();
    public void register(String serverUrl, List<RemoteTool> tools) { fixtures.put(serverUrl, tools); scenarios.put(serverUrl, Scenario.SUCCESS); }
    public void scenario(String serverUrl, Scenario scenario) { scenarios.put(serverUrl, scenario); }
    @Override public List<RemoteTool> listTools(String serverUrl, AuthType authType, String authName, String credential) {
        Scenario s=scenarios.getOrDefault(serverUrl, Scenario.SUCCESS);
        if (s != Scenario.SUCCESS) throw error(s);
        return fixtures.getOrDefault(serverUrl, List.of());
    }
    @Override public JsonNode callTool(String serverUrl, AuthType authType, String authName, String credential, String toolName, Map<String,Object> arguments) {
        for (RemoteTool tool : listTools(serverUrl,authType,authName,credential)) if (tool.name().equals(toolName)) return tool.inputSchema();
        throw new Phase2ContractException(MvpErrorCode.TOOL_NOT_BOUND,"Tool unavailable");
    }
    private Phase2ContractException error(Scenario s) { return new Phase2ContractException(s == Scenario.AUTH_FAIL ? MvpErrorCode.MCP_AUTH_INVALID : s == Scenario.INVALID_SCHEMA || s == Scenario.DUPLICATE_TOOL || s == Scenario.OVERSIZED ? MvpErrorCode.MCP_DISCOVERY_INVALID : s == Scenario.DNS_REBINDING ? MvpErrorCode.MCP_URL_REJECTED : MvpErrorCode.MCP_UNAVAILABLE, "MCP request failed"); }
}
