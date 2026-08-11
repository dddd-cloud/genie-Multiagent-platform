package com.jd.genie.platform.phase2.configuration.agent;

import com.jd.genie.platform.phase2.configuration.skill.binding.entity.AgentSkillBindingEntity;
import com.jd.genie.platform.phase2.configuration.skill.binding.mapper.AgentSkillBindingMapper;
import com.jd.genie.platform.phase2.configuration.support.Phase2AMySqlTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentSkillBindingMapperMySqlTest extends Phase2AMySqlTestSupport {

    @Autowired
    private AgentSkillBindingMapper bindingMapper;

    @Test
    void selectsOwnedBindingsByAgentInSortOrderAndRejectsDuplicates() {
        insert(binding("tenant-a", "owner-a", "agent-a", "skill-b", 2));
        insert(binding("tenant-a", "owner-a", "agent-a", "skill-a", 1));
        insert(binding("tenant-a", "owner-b", "agent-owner-b", "skill-other", 1));

        List<AgentSkillBindingEntity> selected = bindingMapper.selectOwnedBindingsByAgent("tenant-a", "owner-a", "agent-a");
        assertEquals(List.of("skill-a", "skill-b"), selected.stream().map(AgentSkillBindingEntity::getSkillId).toList());
        assertEquals(List.of(1, 2), selected.stream().map(AgentSkillBindingEntity::getSortOrder).toList());
        assertEquals(List.of(), bindingMapper.selectOwnedBindingsByAgent("tenant-a", "owner-b", "agent-missing"));

        assertThrows(DuplicateKeyException.class,
            () -> insert(binding("tenant-a", "owner-a", "agent-a", "skill-a", 3)));
        assertThrows(DuplicateKeyException.class,
            () -> insert(binding("tenant-a", "owner-a", "agent-a", "skill-c", 2)));
        assertEquals(1, bindingMapper.insertBinding(binding("tenant-a", "owner-a", "agent-b", "skill-c", 2)));
    }

    @Test
    void deleteOwnedBindingsByAgentDeletesOnlyTenantAndOwnerScope() {
        insert(binding("tenant-a", "owner-a", "agent-a", "skill-a", 1));
        insert(binding("tenant-a", "owner-a", "agent-a", "skill-b", 2));
        insert(binding("tenant-a", "owner-b", "agent-owner-b", "skill-c", 1));
        insert(binding("tenant-b", "owner-a", "agent-tenant-b", "skill-d", 1));

        assertEquals(2L, bindingMapper.countOwnedReferencesBySkill("tenant-a", "owner-a", "skill-a")
            + bindingMapper.countOwnedReferencesBySkill("tenant-a", "owner-a", "skill-b"));
        assertEquals(0L, bindingMapper.countOwnedReferencesBySkill("tenant-a", "owner-b", "skill-a"));
        assertEquals(2, bindingMapper.deleteOwnedBindingsByAgent("tenant-a", "owner-a", "agent-a"));
        assertEquals(0, bindingMapper.deleteOwnedBindingsByAgent("tenant-a", "owner-a", "agent-a"));

        assertEquals(List.of(), bindingMapper.selectOwnedBindingsByAgent("tenant-a", "owner-a", "agent-a"));
        assertEquals(1, bindingMapper.selectOwnedBindingsByAgent("tenant-a", "owner-b", "agent-owner-b").size());
        assertEquals(1, bindingMapper.selectOwnedBindingsByAgent("tenant-b", "owner-a", "agent-tenant-b").size());
    }

    @Test
    void batchInsertPersistsBindingsAndLetsDatabaseReportConflicts() {
        List<AgentSkillBindingEntity> bindings = List.of(
            binding("tenant-a", "owner-a", "agent-a", "skill-a", 1),
            binding("tenant-a", "owner-a", "agent-a", "skill-b", 2)
        );
        assertEquals(2, bindingMapper.batchInsert(bindings));
        assertEquals(List.of("skill-a", "skill-b"), bindingMapper.selectOwnedBindingsByAgent("tenant-a", "owner-a", "agent-a")
            .stream().map(AgentSkillBindingEntity::getSkillId).toList());

        assertThrows(DuplicateKeyException.class, () -> bindingMapper.batchInsert(List.of(
            binding("tenant-a", "owner-a", "agent-b", "skill-c", 1),
            binding("tenant-a", "owner-a", "agent-b", "skill-c", 2)
        )));
    }

    private void insert(AgentSkillBindingEntity entity) {
        assertEquals(1, bindingMapper.insertBinding(entity));
    }

    private AgentSkillBindingEntity binding(String tenantId, String ownerId, String agentId, String skillId, int sortOrder) {
        AgentSkillBindingEntity entity = new AgentSkillBindingEntity();
        entity.setTenantId(tenantId);
        entity.setOwnerId(ownerId);
        entity.setAgentId(agentId);
        entity.setSkillId(skillId);
        entity.setSortOrder(sortOrder);
        entity.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        return entity;
    }
}