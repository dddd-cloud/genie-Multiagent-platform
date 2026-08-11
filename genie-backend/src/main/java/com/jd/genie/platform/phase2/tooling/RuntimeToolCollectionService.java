package com.jd.genie.platform.phase2.tooling;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.agent.agent.AgentContext;
import com.jd.genie.agent.tool.BaseTool;
import com.jd.genie.agent.tool.ToolCollection;
import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2contract.capability.CapabilityKeys;
import com.jd.genie.platform.phase2contract.dto.AgentRuntimeProfile;
import com.jd.genie.platform.phase2contract.dto.ToolBindingView;
import com.jd.genie.platform.phase2contract.error.Phase2ContractException;
import com.jd.genie.platform.phase2contract.port.RuntimeToolCollectionPort;
import com.jd.genie.platform.phase2contract.port.ToolBindingPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class RuntimeToolCollectionService implements RuntimeToolCollectionPort {
    private final ToolBindingPort bindings;
    private final BuiltinCapabilityCatalog catalog;
    private final JdbcTemplate jdbc;
    private final McpClientAdapter client;
    private final CredentialEnvelopeService credentials;
    private final McpUrlPolicy urlPolicy;
    private final ObjectMapper mapper;

    public RuntimeToolCollectionService(ToolBindingPort bindings, BuiltinCapabilityCatalog catalog, JdbcTemplate jdbc,
                                         McpClientAdapter client, CredentialEnvelopeService credentials, McpUrlPolicy urlPolicy, ObjectMapper mapper) {
        this.bindings = bindings; this.catalog = catalog; this.jdbc = jdbc; this.client = client; this.credentials = credentials; this.urlPolicy = urlPolicy; this.mapper = mapper;
    }

    @Override
    public ToolCollection build(CurrentUser user, AgentRuntimeProfile profile, AgentContext context, List<BaseTool> additionalTools) {
        if (user == null || profile == null || context == null) throw new Phase2ContractException(MvpErrorCode.VALIDATION_ERROR, "user, profile and context are required");
        ToolBindingView view = bindings.resolveBindings(user, profile.agentId(), profile.skills().stream().map(s -> s.skillId()).toList());
        Set<String> selected = new LinkedHashSet<>();
        selected.addAll(view.directCapabilities());
        for (String skillId : profile.skills().stream().map(s -> s.skillId()).toList()) selected.addAll(view.skillCapabilities().getOrDefault(skillId, List.of()));
        Set<String> requested = new LinkedHashSet<>(profile.capabilityKeys());
        if (!requested.isEmpty()) {
            selected.retainAll(requested);
        }
        if (selected.isEmpty() && requested.isEmpty()) {
            selected.add(CapabilityKeys.BUILTIN_DEEP_SEARCH);
        }
        List<BaseTool> tools = new ArrayList<>();
        var builtins = catalog.create(context);
        for (String key : selected) {
            if (catalog.contains(key)) { tools.add(builtins.get(key)); continue; }
            if (CapabilityKeys.isMcp(key)) tools.add(mcpTool(user, key, context));
        }
        if (additionalTools != null) {
            for (BaseTool extra : additionalTools) {
                if (extra == null) {
                    throw new Phase2ContractException(MvpErrorCode.VALIDATION_ERROR, "additional tool must not be null");
                }
                tools.add(extra);
            }
        }
        AuthorizedToolCollection result = new AuthorizedToolCollection(tools);
        result.setAgentContext(context); context.setToolCollection(result);
        return result;
    }

    private UserMcpToolAdapter mcpTool(CurrentUser user, String key, AgentContext context) {
        String toolId = CapabilityKeys.mcpToolId(key);
        return jdbc.query(
                "SELECT t.mcp_server_id,t.runtime_name,t.tool_name,t.description,t.input_schema FROM mcp_tool t JOIN mcp_server s ON s.id=t.mcp_server_id WHERE t.id=? AND t.tenant_id=? AND t.owner_id=? AND t.enabled=TRUE AND t.available=TRUE AND s.status='ENABLED' AND s.deleted_at IS NULL",
                rs -> {
                    if (!rs.next()) {
                        throw new ToolCapabilityException("tool is not bound");
                    }
                    try {
                        JsonNode schema = mapper.readTree(rs.getString(5));
                        return new UserMcpToolAdapter(
                                jdbc,
                                client,
                                credentials,
                                urlPolicy,
                                mapper,
                                user,
                                toolId,
                                rs.getString(1),
                                key,
                                rs.getString(2),
                                rs.getString(3),
                                rs.getString(4),
                                schema
                        );
                    } catch (Exception ex) {
                        throw new Phase2ContractException(MvpErrorCode.TOOL_INVALID_RESPONSE, "tool schema invalid", ex);
                    }
                },
                toolId,
                user.tenantId(),
                user.userId()
        );
    }
}
