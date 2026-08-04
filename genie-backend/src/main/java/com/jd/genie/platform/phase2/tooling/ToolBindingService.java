package com.jd.genie.platform.phase2.tooling;

import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2contract.capability.CapabilityKeys;
import com.jd.genie.platform.phase2contract.dto.ToolBindingView;
import com.jd.genie.platform.phase2contract.error.Phase2ContractException;
import com.jd.genie.platform.phase2contract.port.ToolBindingPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
public class ToolBindingService implements ToolBindingPort {
    private static final int MAX_BINDINGS = 50;
    private final JdbcTemplate jdbc;

    public ToolBindingService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public ToolBindingView resolveBindings(CurrentUser user, String agentId, List<String> enabledSkillIds) {
        requireUser(user);
        requireId(agentId, "agentId");
        if (enabledSkillIds == null) {
            throw validation("enabledSkillIds must not be null");
        }
        List<String> invalid = new ArrayList<>();
        List<String> direct = readCapabilities("SELECT capability_key FROM agent_tool_binding WHERE tenant_id=? AND owner_id=? AND agent_id=? ORDER BY capability_key", user, agentId, invalid);
        Map<String, List<String>> skills = new LinkedHashMap<>();
        for (String skillId : enabledSkillIds) {
            requireId(skillId, "skillId");
            skills.put(skillId, readCapabilities("SELECT capability_key FROM skill_tool_binding WHERE tenant_id=? AND owner_id=? AND skill_id=? ORDER BY capability_key", user, skillId, invalid));
        }
        return new ToolBindingView(direct, skills, List.copyOf(new LinkedHashSet<>(invalid)));
    }

    @Override
    @Transactional
    public void replaceAgentBindings(CurrentUser user, String agentId, List<String> capabilityKeys) {
        replace(user, agentId, capabilityKeys, true);
    }

    @Override
    @Transactional
    public void replaceSkillBindings(CurrentUser user, String skillId, List<String> capabilityKeys) {
        replace(user, skillId, capabilityKeys, false);
    }

    @Override
    @Transactional
    public void removeAgentBindings(CurrentUser user, String agentId) {
        remove(user, agentId, true);
    }

    @Override
    @Transactional
    public void removeSkillBindings(CurrentUser user, String skillId) {
        remove(user, skillId, false);
    }

    private void replace(CurrentUser user, String resourceId, List<String> capabilityKeys, boolean agent) {
        requireUser(user);
        requireId(resourceId, agent ? "agentId" : "skillId");
        if (capabilityKeys == null) throw validation("capabilityKeys must not be null");
        if (capabilityKeys.size() > MAX_BINDINGS) throw validation("too many capability keys");
        List<String> keys = List.copyOf(CapabilityKeys.requireAllValid(capabilityKeys));
        for (String key : keys) {
            if (!capabilityAvailable(user, key)) {
                throw new Phase2ContractException(MvpErrorCode.TOOL_BINDING_INVALID, "capability is not available");
            }
        }
        String table = agent ? "agent_tool_binding" : "skill_tool_binding";
        String idColumn = agent ? "agent_id" : "skill_id";
        jdbc.update("DELETE FROM " + table + " WHERE tenant_id=? AND owner_id=? AND " + idColumn + "=?", user.tenantId(), user.userId(), resourceId);
        for (String key : keys) {
            jdbc.update("INSERT INTO " + table + " (tenant_id,owner_id," + idColumn + ",capability_key,created_at) VALUES (?,?,?,?,?)", user.tenantId(), user.userId(), resourceId, key, LocalDateTime.now());
        }
    }

    private void remove(CurrentUser user, String resourceId, boolean agent) {
        requireUser(user);
        requireId(resourceId, agent ? "agentId" : "skillId");
        String table = agent ? "agent_tool_binding" : "skill_tool_binding";
        String idColumn = agent ? "agent_id" : "skill_id";
        jdbc.update("DELETE FROM " + table + " WHERE tenant_id=? AND owner_id=? AND " + idColumn + "=?", user.tenantId(), user.userId(), resourceId);
    }

    private List<String> readCapabilities(String sql, CurrentUser user, String resourceId, List<String> invalid) {
        List<String> configured = jdbc.queryForList(sql, String.class, user.tenantId(), user.userId(), resourceId);
        List<String> valid = new ArrayList<>();
        for (String key : configured) {
            if (isValidAndAvailable(user, key)) valid.add(key); else invalid.add(key);
        }
        return List.copyOf(valid);
    }

    private boolean capabilityAvailable(CurrentUser user, String key) {
        return CapabilityKeys.isBuiltIn(key) || jdbc.queryForObject("SELECT COUNT(*) FROM mcp_tool t JOIN mcp_server s ON s.id=t.mcp_server_id WHERE t.id=? AND t.tenant_id=? AND t.owner_id=? AND t.enabled=TRUE AND t.available=TRUE AND s.tenant_id=? AND s.owner_id=? AND s.status='ENABLED' AND s.deleted_at IS NULL", Integer.class, CapabilityKeys.mcpToolId(key), user.tenantId(), user.userId(), user.tenantId(), user.userId()) > 0;
    }

    private boolean isValidAndAvailable(CurrentUser user, String key) {
        try { CapabilityKeys.requireValid(key); return capabilityAvailable(user, key); }
        catch (RuntimeException ex) { return false; }
    }

    private static void requireUser(CurrentUser user) {
        if (user == null || blank(user.tenantId()) || blank(user.userId())) throw validation("user must not be null");
    }
    private static void requireId(String value, String name) { if (blank(value)) throw validation(name + " must not be blank"); }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static Phase2ContractException validation(String message) { return new Phase2ContractException(MvpErrorCode.VALIDATION_ERROR, message); }
}
