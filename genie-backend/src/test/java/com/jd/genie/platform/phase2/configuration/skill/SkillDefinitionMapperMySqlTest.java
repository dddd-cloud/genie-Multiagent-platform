package com.jd.genie.platform.phase2.configuration.skill;

import com.jd.genie.platform.phase2.configuration.skill.entity.SkillDefinitionEntity;
import com.jd.genie.platform.phase2.configuration.skill.mapper.SkillDefinitionMapper;
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

class SkillDefinitionMapperMySqlTest extends Phase2AMySqlTestSupport {

    @Autowired
    private SkillDefinitionMapper skillMapper;

    @Test
    void insertsSelectsAndRejectsCrossTenantOrOwnerReads() {
        SkillDefinitionEntity skill = skill("skill-a", "tenant-a", "owner-a", "search", Instant.parse("2026-01-01T00:00:00Z"));
        assertEquals(1, skillMapper.insert(skill));

        SkillDefinitionEntity selected = skillMapper.selectOwnedById("tenant-a", "owner-a", "skill-a");
        assertNotNull(selected);
        assertEquals("search", selected.getName());
        assertEquals("instruction for search", selected.getInstruction());
        assertNull(selected.getOutputRequirement());
        assertEquals(0L, selected.getVersion());

        assertNull(skillMapper.selectOwnedById("tenant-b", "owner-a", "skill-a"));
        assertNull(skillMapper.selectOwnedById("tenant-a", "owner-b", "skill-a"));
        assertEquals(1L, skillMapper.countOwned("tenant-a", "owner-a"));
        assertEquals(0L, skillMapper.countOwned("tenant-a", "owner-b"));
    }

    @Test
    void selectsStablePageWithPageSizePlusOne() {
        insert(skill("skill-old", "tenant-a", "owner-a", "old", Instant.parse("2026-01-01T00:00:00Z")));
        insert(skill("skill-mid", "tenant-a", "owner-a", "mid", Instant.parse("2026-01-02T00:00:00Z")));
        insert(skill("skill-new-b", "tenant-a", "owner-a", "new-b", Instant.parse("2026-01-03T00:00:00Z")));
        insert(skill("skill-new-a", "tenant-a", "owner-a", "new-a", Instant.parse("2026-01-03T00:00:00Z")));
        insert(skill("skill-other", "tenant-a", "owner-b", "other", Instant.parse("2026-01-04T00:00:00Z")));

        List<SkillDefinitionEntity> page = skillMapper.selectOwnedPage("tenant-a", "owner-a", 3, 0);
        assertEquals(List.of("skill-new-b", "skill-new-a", "skill-mid"), page.stream().map(SkillDefinitionEntity::getId).toList());
    }

    @Test
    void updatesOnlyOwnedActiveRecordWithVersionAndDoesNotChangeStatus() {
        insert(skill("skill-a", "tenant-a", "owner-a", "search", Instant.parse("2026-01-01T00:00:00Z")));
        SkillDefinitionEntity update = skillMapper.selectOwnedById("tenant-a", "owner-a", "skill-a");
        update.setName("search-renamed");
        update.setDescription("updated description");
        update.setInstruction("updated instruction");
        update.setOutputRequirement("return markdown");
        update.setStatus("DISABLED");

        Instant updatedAt = Instant.parse("2026-01-02T00:00:00Z");
        assertEquals(1, skillMapper.updateOwnedWithVersion("tenant-a", "owner-a", update, 0L, updatedAt));
        assertEquals(0, skillMapper.updateOwnedWithVersion("tenant-a", "owner-a", update, 0L, updatedAt));
        assertEquals(0, skillMapper.updateOwnedWithVersion("tenant-a", "owner-b", update, 1L, updatedAt));

        SkillDefinitionEntity selected = skillMapper.selectOwnedById("tenant-a", "owner-a", "skill-a");
        assertEquals("search-renamed", selected.getName());
        assertEquals("updated instruction", selected.getInstruction());
        assertEquals("return markdown", selected.getOutputRequirement());
        assertEquals("ENABLED", selected.getStatus());
        assertEquals(1L, selected.getVersion());
        assertEquals(1L, skillMapper.selectOwnedVersion("tenant-a", "owner-a", "skill-a"));
    }

    @Test
    void softDeleteHidesRecordAllowsNameReuseAndProtectsActiveNameUniqueness() {
        insert(skill("skill-a", "tenant-a", "owner-a", "search", Instant.parse("2026-01-01T00:00:00Z")));
        assertTrue(skillMapper.existsOwnedActiveName("tenant-a", "owner-a", "search", null));
        assertFalse(skillMapper.existsOwnedActiveName("tenant-a", "owner-a", "search", "skill-a"));
        assertThrows(DuplicateKeyException.class,
            () -> insert(skill("skill-dup", "tenant-a", "owner-a", "search", Instant.parse("2026-01-01T01:00:00Z"))));

        Instant deletedAt = Instant.parse("2026-01-02T00:00:00Z");
        assertEquals(1, skillMapper.softDeleteOwnedWithVersion("tenant-a", "owner-a", "skill-a", 0L, deletedAt));
        assertEquals(0, skillMapper.softDeleteOwnedWithVersion("tenant-a", "owner-a", "skill-a", 1L, deletedAt));
        assertNull(skillMapper.selectOwnedById("tenant-a", "owner-a", "skill-a"));
        assertFalse(skillMapper.existsOwnedActiveName("tenant-a", "owner-a", "search", null));
        assertEquals(1L, jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM skill_definition WHERE id = 'skill-a' AND deleted_at IS NOT NULL", Long.class));

        assertEquals(1, skillMapper.insert(skill("skill-reuse", "tenant-a", "owner-a", "search", Instant.parse("2026-01-03T00:00:00Z"))));
    }

    private void insert(SkillDefinitionEntity entity) {
        assertEquals(1, skillMapper.insert(entity));
    }

    private SkillDefinitionEntity skill(String id, String tenantId, String ownerId, String name, Instant timestamp) {
        SkillDefinitionEntity entity = new SkillDefinitionEntity();
        entity.setId(id);
        entity.setTenantId(tenantId);
        entity.setOwnerId(ownerId);
        entity.setName(name);
        entity.setDescription("description for " + name);
        entity.setInstruction("instruction for " + name);
        entity.setOutputRequirement(null);
        entity.setStatus("ENABLED");
        entity.setCreatedAt(timestamp);
        entity.setUpdatedAt(timestamp);
        return entity;
    }
}