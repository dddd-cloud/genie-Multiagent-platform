package com.jd.genie.platform.phase2.configuration.skill;

import com.jd.genie.platform.phase2.configuration.skill.dto.SkillCreateRequest;
import com.jd.genie.platform.phase2.configuration.skill.dto.SkillResponse;
import com.jd.genie.platform.phase2.configuration.skill.service.SkillDefinitionService;
import com.jd.genie.platform.phase2.configuration.support.Phase2AMySqlTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SkillStateTransitionTest extends Phase2AMySqlTestSupport {

    @Autowired
    private SkillDefinitionService skillService;

    @Test
    void supportsEnableDisableAndIdempotentRepeats() {
        SkillResponse created = skillService.createSkill(userA(), new SkillCreateRequest("Skill", "desc", "instruction", null, List.of()));
        SkillResponse disabled = skillService.disableSkill(userA(), created.id(), created.version());
        assertEquals("DISABLED", disabled.status());
        SkillResponse stillDisabled = skillService.disableSkill(userA(), created.id(), disabled.version());
        assertEquals(disabled.version(), stillDisabled.version());
        SkillResponse enabled = skillService.enableSkill(userA(), created.id(), disabled.version());
        assertEquals("ENABLED", enabled.status());
    }
}