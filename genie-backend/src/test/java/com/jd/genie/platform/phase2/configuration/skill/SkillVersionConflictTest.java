package com.jd.genie.platform.phase2.configuration.skill;

import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2.configuration.skill.dto.SkillCreateRequest;
import com.jd.genie.platform.phase2.configuration.skill.dto.SkillResponse;
import com.jd.genie.platform.phase2.configuration.skill.dto.SkillUpdateRequest;
import com.jd.genie.platform.phase2.configuration.skill.exception.SkillConfigurationException;
import com.jd.genie.platform.phase2.configuration.skill.service.SkillDefinitionService;
import com.jd.genie.platform.phase2.configuration.support.Phase2AMySqlTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillVersionConflictTest extends Phase2AMySqlTestSupport {

    @Autowired
    private SkillDefinitionService skillService;

    @Test
    void staleVersionReturnsVersionConflictAndDoesNotCallToolBinding() {
        SkillResponse created = skillService.createSkill(userA(), new SkillCreateRequest("Skill", "desc", "instruction", null, List.of()));
        skillService.updateSkill(userA(), created.id(), new SkillUpdateRequest(0L, "Skill v2", "desc", "instruction", null, List.of()));
        fakeToolBindingPort.reset();

        SkillConfigurationException error = assertThrows(SkillConfigurationException.class,
            () -> skillService.updateSkill(userA(), created.id(), new SkillUpdateRequest(0L, "Skill v3", "desc", "instruction", null, List.of())));

        assertEquals(MvpErrorCode.VERSION_CONFLICT, error.code());
        assertTrue(fakeToolBindingPort.getCalls().isEmpty());
    }
}