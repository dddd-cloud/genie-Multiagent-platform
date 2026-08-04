package com.jd.genie.platform.phase2.configuration.skill;

import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2.configuration.agent.dto.AgentCreateRequest;
import com.jd.genie.platform.phase2.configuration.agent.dto.AgentResponse;
import com.jd.genie.platform.phase2.configuration.agent.dto.AgentSkillBindingRequest;
import com.jd.genie.platform.phase2.configuration.agent.dto.AgentUpdateRequest;
import com.jd.genie.platform.phase2.configuration.agent.exception.AgentConfigurationException;
import com.jd.genie.platform.phase2.configuration.agent.mapper.AgentSkillBindingMapper;
import com.jd.genie.platform.phase2.configuration.agent.service.AgentDefinitionService;
import com.jd.genie.platform.phase2.configuration.skill.dto.SkillCreateRequest;
import com.jd.genie.platform.phase2.configuration.skill.dto.SkillResponse;
import com.jd.genie.platform.phase2.configuration.skill.service.SkillDefinitionService;
import com.jd.genie.platform.phase2.configuration.support.Phase2AMySqlTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SkillOrderingTransactionTest extends Phase2AMySqlTestSupport {

    @Autowired
    private SkillDefinitionService skillService;
    @Autowired
    private AgentDefinitionService agentService;
    @Autowired
    private AgentSkillBindingMapper bindingMapper;

    @Test
    void validatesConsecutiveOrderingAndReplacesBindingsAtomically() {
        SkillResponse first = skillService.createSkill(userA(), new SkillCreateRequest("First", "desc", "instruction", null, List.of()));
        SkillResponse second = skillService.createSkill(userA(), new SkillCreateRequest("Second", "desc", "instruction", null, List.of()));
        AgentResponse agent = agentService.createAgent(userA(), new AgentCreateRequest("Agent", "desc", null, null, "prompt", null,
            List.of(new AgentSkillBindingRequest(first.id(), 1)), List.of()));

        AgentConfigurationException invalid = assertThrows(AgentConfigurationException.class,
            () -> agentService.updateAgent(userA(), agent.id(), new AgentUpdateRequest(agent.version(), "Agent", "desc", null, null, "prompt", null,
                List.of(new AgentSkillBindingRequest(first.id(), 1), new AgentSkillBindingRequest(second.id(), 3)), List.of())));
        assertEquals(MvpErrorCode.VALIDATION_ERROR, invalid.code());
        assertEquals(List.of(first.id()), bindingMapper.selectOwnedBindingsByAgent(userA().tenantId(), userA().userId(), agent.id())
            .stream().map(row -> row.getSkillId()).toList());

        AgentResponse updated = agentService.updateAgent(userA(), agent.id(), new AgentUpdateRequest(agent.version(), "Agent", "desc", null, null, "prompt", null,
            List.of(new AgentSkillBindingRequest(second.id(), 1), new AgentSkillBindingRequest(first.id(), 2)), List.of()));
        assertEquals(1L, updated.version());
        assertEquals(List.of(second.id(), first.id()), bindingMapper.selectOwnedBindingsByAgent(userA().tenantId(), userA().userId(), agent.id())
            .stream().map(row -> row.getSkillId()).toList());
    }
}