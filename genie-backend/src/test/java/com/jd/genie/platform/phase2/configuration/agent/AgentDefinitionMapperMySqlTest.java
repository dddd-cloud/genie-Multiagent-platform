package com.jd.genie.platform.phase2.configuration.agent;

import com.jd.genie.platform.phase2.configuration.agent.entity.AgentDefinitionEntity;
import com.jd.genie.platform.phase2.configuration.agent.mapper.AgentDefinitionMapper;
import com.jd.genie.platform.phase2.configuration.support.Phase2AMySqlTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentDefinitionMapperMySqlTest extends Phase2AMySqlTestSupport {

    @Autowired
    private AgentDefinitionMapper agentMapper;

    @Test
    void insertsSelectsAndRejectsCrossTenantOrOwnerReads() {
        AgentDefinitionEntity agent = agent("agent-a", "tenant-a", "owner-a", "helper", Instant.parse("2026-01-01T00:00:00Z"));
        assertEquals(1, agentMapper.insert(agent));

        AgentDefinitionEntity selected = agentMapper.selectOwnedById("tenant-a", "owner-a", "agent-a");
        assertNotNull(selected);
        assertEquals("helper", selected.getName());
        assertEquals("RAW", selected.getPromptMode());
        assertEquals("qwen-plus", selected.getModelName());
        assertEquals(0L, selected.getVersion());

        assertNull(agentMapper.selectOwnedById("tenant-b", "owner-a", "agent-a"));
        assertNull(agentMapper.selectOwnedById("tenant-a", "owner-b", "agent-a"));
        assertEquals(1L, agentMapper.countOwned("tenant-a", "owner-a"));
        assertEquals(0L, agentMapper.countOwned("tenant-a", "owner-b"));
    }

    @Test
    void selectsStablePageWithPageSizePlusOneAndOwnedIds() {
        insert(agent("agent-old", "tenant-a", "owner-a", "old", Instant.parse("2026-01-01T00:00:00Z")));
        insert(agent("agent-mid", "tenant-a", "owner-a", "mid", Instant.parse("2026-01-02T00:00:00Z")));
        insert(agent("agent-new-b", "tenant-a", "owner-a", "new-b", Instant.parse("2026-01-03T00:00:00Z")));
        insert(agent("agent-new-a", "tenant-a", "owner-a", "new-a", Instant.parse("2026-01-03T00:00:00Z")));
        insert(agent("agent-other", "tenant-a", "owner-b", "other", Instant.parse("2026-01-04T00:00:00Z")));

        List<AgentDefinitionEntity> page = agentMapper.selectOwnedPage("tenant-a", "owner-a", 3, 0);
        assertEquals(List.of("agent-new-b", "agent-new-a", "agent-mid"), page.stream().map(AgentDefinitionEntity::getId).toList());

        List<AgentDefinitionEntity> byIds = agentMapper.selectOwnedByIds("tenant-a", "owner-a", List.of("agent-old", "agent-other"));
        assertEquals(List.of("agent-old"), byIds.stream().map(AgentDefinitionEntity::getId).toList());
    }

    @Test
    void updatesOnlyOwnedActiveRecordWithVersionAndDoesNotChangeStatus() {
        insert(agent("agent-a", "tenant-a", "owner-a", "helper", Instant.parse("2026-01-01T00:00:00Z")));
        AgentDefinitionEntity update = agentMapper.selectOwnedById("tenant-a", "owner-a", "agent-a");
        update.setName("helper-renamed");
        update.setDescription("updated description");
        update.setPromptMode("TEMPLATE");
        update.setPromptConfig("{\"sections\":[\"goal\"]}");
        update.setSystemPrompt("updated prompt");
        update.setModelName(null);
        update.setStatus("ONLINE");

        Instant updatedAt = Instant.parse("2026-01-02T00:00:00Z");
        assertEquals(1, agentMapper.updateOwnedWithVersion("tenant-a", "owner-a", update, 0L, updatedAt));
        assertEquals(0, agentMapper.updateOwnedWithVersion("tenant-a", "owner-a", update, 0L, updatedAt));
        assertEquals(0, agentMapper.updateOwnedWithVersion("tenant-a", "owner-b", update, 1L, updatedAt));

        AgentDefinitionEntity selected = agentMapper.selectOwnedById("tenant-a", "owner-a", "agent-a");
        assertEquals("helper-renamed", selected.getName());
        assertEquals("TEMPLATE", selected.getPromptMode());
        assertTrue(selected.getPromptConfig().contains("\"sections\""));
        assertTrue(selected.getPromptConfig().contains("\"goal\""));
        assertNull(selected.getModelName());
        assertEquals("DRAFT", selected.getStatus());
        assertEquals(1L, selected.getVersion());
        assertEquals(1L, agentMapper.selectOwnedVersion("tenant-a", "owner-a", "agent-a"));
    }

    @Test
    void softDeleteHidesRecordAllowsNameReuseAndProtectsActiveNameUniqueness() {
        insert(agent("agent-a", "tenant-a", "owner-a", "helper", Instant.parse("2026-01-01T00:00:00Z")));
        assertTrue(agentMapper.existsOwnedActiveName("tenant-a", "owner-a", "helper", null));
        assertFalse(agentMapper.existsOwnedActiveName("tenant-a", "owner-a", "helper", "agent-a"));
        assertThrows(DuplicateKeyException.class,
            () -> insert(agent("agent-dup", "tenant-a", "owner-a", "helper", Instant.parse("2026-01-01T01:00:00Z"))));

        Instant deletedAt = Instant.parse("2026-01-02T00:00:00Z");
        assertEquals(1, agentMapper.softDeleteOwnedWithVersion("tenant-a", "owner-a", "agent-a", 0L, deletedAt));
        assertEquals(0, agentMapper.softDeleteOwnedWithVersion("tenant-a", "owner-a", "agent-a", 1L, deletedAt));
        assertNull(agentMapper.selectOwnedById("tenant-a", "owner-a", "agent-a"));
        assertFalse(agentMapper.existsOwnedActiveName("tenant-a", "owner-a", "helper", null));
        assertEquals(1L, jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM agent_definition WHERE id = 'agent-a' AND deleted_at IS NOT NULL", Long.class));

        assertEquals(1, agentMapper.insert(agent("agent-reuse", "tenant-a", "owner-a", "helper", Instant.parse("2026-01-03T00:00:00Z"))));
    }

    private void insert(AgentDefinitionEntity entity) {
        assertEquals(1, agentMapper.insert(entity));
    }

    private AgentDefinitionEntity agent(String id, String tenantId, String ownerId, String name, Instant timestamp) {
        AgentDefinitionEntity entity = new AgentDefinitionEntity();
        entity.setId(id);
        entity.setTenantId(tenantId);
        entity.setOwnerId(ownerId);
        entity.setName(name);
        entity.setDescription("description for " + name);
        entity.setPromptMode("RAW");
        entity.setPromptConfig(null);
        entity.setSystemPrompt("system prompt for " + name);
        entity.setModelName("qwen-plus");
        entity.setStatus("DRAFT");
        entity.setCreatedAt(timestamp);
        entity.setUpdatedAt(timestamp);
        return entity;
    }
}