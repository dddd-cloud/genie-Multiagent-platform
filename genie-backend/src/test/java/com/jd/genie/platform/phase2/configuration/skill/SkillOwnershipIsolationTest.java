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

class SkillOwnershipIsolationTest extends Phase2AMySqlTestSupport {

    @Autowired
    private SkillDefinitionService skillService;

    @Test
    void crossOwnerAndTenantRequestsReturnNotFoundAndDoNotCallToolBinding() {
        SkillResponse created = skillService.createSkill(userA(), new SkillCreateRequest("Skill", "desc", "instruction", null, List.of()));
        fakeToolBindingPort.reset();

        SkillConfigurationException ownerError = assertThrows(SkillConfigurationException.class,
            () -> skillService.updateSkill(userB(), created.id(), new SkillUpdateRequest(0L, "x", "desc", "instruction", null, List.of())));
        SkillConfigurationException tenantError = assertThrows(SkillConfigurationException.class,
            () -> skillService.getSkill(tenantBUser(), created.id()));

        assertEquals(MvpErrorCode.RESOURCE_NOT_FOUND, ownerError.code());
        assertEquals(MvpErrorCode.RESOURCE_NOT_FOUND, tenantError.code());
        assertTrue(fakeToolBindingPort.getCalls().isEmpty());
    }
}