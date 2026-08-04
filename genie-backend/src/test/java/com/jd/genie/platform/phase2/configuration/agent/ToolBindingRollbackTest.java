package com.jd.genie.platform.phase2.configuration.agent;

import com.jd.genie.platform.phase2.configuration.agent.dto.AgentCreateRequest;
import com.jd.genie.platform.phase2.configuration.agent.dto.AgentResponse;
import com.jd.genie.platform.phase2.configuration.agent.dto.AgentSkillBindingRequest;
import com.jd.genie.platform.phase2.configuration.agent.dto.AgentUpdateRequest;
import com.jd.genie.platform.phase2.configuration.agent.mapper.AgentDefinitionMapper;
import com.jd.genie.platform.phase2.configuration.agent.mapper.AgentSkillBindingMapper;
import com.jd.genie.platform.phase2.configuration.agent.service.AgentDefinitionService;
import com.jd.genie.platform.phase2.configuration.skill.dto.SkillCreateRequest;
import com.jd.genie.platform.phase2.configuration.skill.dto.SkillResponse;
import com.jd.genie.platform.phase2.configuration.skill.dto.SkillUpdateRequest;
import com.jd.genie.platform.phase2.configuration.skill.mapper.SkillDefinitionMapper;
import com.jd.genie.platform.phase2.configuration.skill.service.SkillDefinitionService;
import com.jd.genie.platform.phase2.configuration.support.Phase2AMySqlTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ToolBindingRollbackTest extends Phase2AMySqlTestSupport {

    @Autowired
    private AgentDefinitionService agentService;
    @Autowired
    private SkillDefinitionService skillService;
    @Autowired
    private AgentDefinitionMapper agentMapper;
    @Autowired
    private SkillDefinitionMapper skillMapper;
    @Autowired
    private AgentSkillBindingMapper bindingMapper;

    @Test
    void agentUpdateRollsBackWhenToolBindingFails() {
        AgentResponse agent = agentService.createAgent(userA(), new AgentCreateRequest("Agent", "desc", null, null, "prompt", null, List.of(), List.of()));
        fakeToolBindingPort.setWriteException(new IllegalStateException("tool write failed"));

        assertThrows(IllegalStateException.class, () -> agentService.updateAgent(userA(), agent.id(),
            new AgentUpdateRequest(agent.version(), "Changed", "desc", null, null, "prompt", null, List.of(), List.of())));

        assertEquals("Agent", agentMapper.selectOwnedById(userA().tenantId(), userA().userId(), agent.id()).getName());
        assertEquals(0L, agentMapper.selectOwnedVersion(userA().tenantId(), userA().userId(), agent.id()));
    }

    @Test
    void skillUpdateRollsBackWhenToolBindingFails() {
        SkillResponse skill = skillService.createSkill(userA(), new SkillCreateRequest("Skill", "desc", "instruction", null, List.of()));
        fakeToolBindingPort.setWriteException(new IllegalStateException("tool write failed"));

        assertThrows(IllegalStateException.class, () -> skillService.updateSkill(userA(), skill.id(),
            new SkillUpdateRequest(skill.version(), "Changed", "desc", "instruction", null, List.of())));

        assertEquals("Skill", skillMapper.selectOwnedById(userA().tenantId(), userA().userId(), skill.id()).getName());
        assertEquals(0L, skillMapper.selectOwnedVersion(userA().tenantId(), userA().userId(), skill.id()));
    }

    @Test
    void bindingReplaceRollsBackWhenToolBindingFails() {
        SkillResponse skill = skillService.createSkill(userA(), new SkillCreateRequest("Skill", "desc", "instruction", null, List.of()));
        AgentResponse agent = agentService.createAgent(userA(), new AgentCreateRequest("Agent", "desc", null, null, "prompt", null, List.of(), List.of()));
        fakeToolBindingPort.setWriteException(new IllegalStateException("tool write failed"));

        assertThrows(IllegalStateException.class, () -> agentService.updateAgent(userA(), agent.id(),
            new AgentUpdateRequest(agent.version(), "Agent", "desc", null, null, "prompt", null,
                List.of(new AgentSkillBindingRequest(skill.id(), 1)), List.of())));

        assertEquals(List.of(), bindingMapper.selectOwnedBindingsByAgent(userA().tenantId(), userA().userId(), agent.id()));
        assertEquals(0L, agentMapper.selectOwnedVersion(userA().tenantId(), userA().userId(), agent.id()));
    }

    @Test
    void removeBindingsFailuresRollBackDeletes() {
        SkillResponse skill = skillService.createSkill(userA(), new SkillCreateRequest("Skill", "desc", "instruction", null, List.of()));
        AgentResponse agent = agentService.createAgent(userA(), new AgentCreateRequest("Agent", "desc", null, null, "prompt", null,
            List.of(new AgentSkillBindingRequest(skill.id(), 1)), List.of()));
        AgentResponse offline = agentService.offlineAgent(userA(), agent.id(), agent.version());
        fakeToolBindingPort.setWriteException(new IllegalStateException("tool write failed"));

        assertThrows(IllegalStateException.class, () -> agentService.deleteAgent(userA(), agent.id(), offline.version()));
        assertNotNull(agentMapper.selectOwnedById(userA().tenantId(), userA().userId(), agent.id()));
        assertEquals(1, bindingMapper.selectOwnedBindingsByAgent(userA().tenantId(), userA().userId(), agent.id()).size());
    }

    @Test
    void skillRemoveBindingsFailureRollsBackDelete() {
        SkillResponse skill = skillService.createSkill(userA(), new SkillCreateRequest("Skill", "desc", "instruction", null, List.of()));
        fakeToolBindingPort.setWriteException(new IllegalStateException("tool write failed"));

        assertThrows(IllegalStateException.class, () -> skillService.deleteSkill(userA(), skill.id(), skill.version()));

        assertNotNull(skillMapper.selectOwnedById(userA().tenantId(), userA().userId(), skill.id()));
        assertEquals(0L, skillMapper.selectOwnedVersion(userA().tenantId(), userA().userId(), skill.id()));
    }
}
