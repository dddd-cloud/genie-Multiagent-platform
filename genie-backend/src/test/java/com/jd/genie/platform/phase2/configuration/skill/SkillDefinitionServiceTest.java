package com.jd.genie.platform.phase2.configuration.skill;

import com.jd.genie.platform.contract.PageResponse;
import com.jd.genie.platform.phase2.configuration.skill.dto.SkillCreateRequest;
import com.jd.genie.platform.phase2.configuration.skill.dto.SkillResponse;
import com.jd.genie.platform.phase2.configuration.skill.dto.SkillUpdateRequest;
import com.jd.genie.platform.phase2.configuration.skill.service.SkillDefinitionService;
import com.jd.genie.platform.phase2.configuration.support.Phase2AMySqlTestSupport;
import com.jd.genie.platform.phase2contract.capability.CapabilityKeys;
import com.jd.genie.platform.phase2contract.support.FakeToolBindingPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillDefinitionServiceTest extends Phase2AMySqlTestSupport {

    @Autowired
    private SkillDefinitionService skillService;

    @Test
    void createsListsGetsAndUpdatesSkillWithoutChangingStatus() {
        SkillResponse created = skillService.createSkill(userA(), new SkillCreateRequest("Summarize", "desc", "instruction", null,
            List.of(CapabilityKeys.BUILTIN_FILE, CapabilityKeys.BUILTIN_FILE)));
        assertEquals("ENABLED", created.status());
        assertEquals(List.of(CapabilityKeys.BUILTIN_FILE), created.capabilityKeys());

        PageResponse<SkillResponse> page = skillService.listSkills(userA(), 1, 1);
        assertEquals(1, page.items().size());
        assertFalse(page.hasMore());
        assertEquals(created.id(), skillService.getSkill(userA(), created.id()).id());

        SkillResponse updated = skillService.updateSkill(userA(), created.id(), new SkillUpdateRequest(
            created.version(), "Summarize v2", "updated", "updated instruction", "markdown", List.of()));
        assertEquals("Summarize v2", updated.name());
        assertEquals("ENABLED", updated.status());
        assertEquals(1L, updated.version());
        assertTrue(fakeToolBindingPort.getCalls().stream()
            .anyMatch(call -> call.type() == FakeToolBindingPort.CallType.REPLACE_SKILL_BINDINGS));
    }
}