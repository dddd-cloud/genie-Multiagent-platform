package com.jd.genie.platform.phase2.configuration.skill;

import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2.configuration.agent.dto.AgentCreateRequest;
import com.jd.genie.platform.phase2.configuration.agent.dto.AgentSkillBindingRequest;
import com.jd.genie.platform.phase2.configuration.agent.service.AgentDefinitionService;
import com.jd.genie.platform.phase2.configuration.skill.dto.SkillCreateRequest;
import com.jd.genie.platform.phase2.configuration.skill.dto.SkillResponse;
import com.jd.genie.platform.phase2.configuration.skill.exception.SkillConfigurationException;
import com.jd.genie.platform.phase2.configuration.skill.service.SkillDefinitionService;
import com.jd.genie.platform.phase2.configuration.support.Phase2AMySqlTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SkillInUseDeleteTest extends Phase2AMySqlTestSupport {

    @Autowired
    private SkillDefinitionService skillService;
    @Autowired
    private AgentDefinitionService agentService;

    @Test
    void deleteSkillFailsWhenReferencedByUndeletedAgent() {
        SkillResponse skill = skillService.createSkill(userA(), new SkillCreateRequest("Skill", "desc", "instruction", null, List.of()));
        agentService.createAgent(userA(), new AgentCreateRequest("Agent", "desc", null, null, "prompt", null,
            List.of(new AgentSkillBindingRequest(skill.id(), 1)), List.of()));

        SkillConfigurationException error = assertThrows(SkillConfigurationException.class,
            () -> skillService.deleteSkill(userA(), skill.id(), skill.version()));
        assertEquals(MvpErrorCode.SKILL_IN_USE, error.code());
    }
}