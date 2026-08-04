package com.jd.genie.platform.phase2.configuration.agent;

import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2.configuration.agent.dto.AgentCreateRequest;
import com.jd.genie.platform.phase2.configuration.agent.dto.AgentResponse;
import com.jd.genie.platform.phase2.configuration.agent.dto.AgentSkillBindingRequest;
import com.jd.genie.platform.phase2.configuration.agent.exception.AgentConfigurationException;
import com.jd.genie.platform.phase2.configuration.agent.service.AgentDefinitionService;
import com.jd.genie.platform.phase2.configuration.skill.dto.SkillCreateRequest;
import com.jd.genie.platform.phase2.configuration.skill.dto.SkillResponse;
import com.jd.genie.platform.phase2.configuration.skill.service.SkillDefinitionService;
import com.jd.genie.platform.phase2.configuration.support.Phase2AMySqlTestSupport;
import com.jd.genie.platform.phase2contract.dto.ToolBindingView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentOnlineValidationTest extends Phase2AMySqlTestSupport {

    @Autowired
    private AgentDefinitionService agentService;
    @Autowired
    private SkillDefinitionService skillService;

    @Test
    void onlineRejectsDisabledSkillAndInvalidResolvedCapabilities() {
        SkillResponse skill = skillService.createSkill(userA(), new SkillCreateRequest("Skill", "desc", "instruction", null, List.of()));
        skillService.disableSkill(userA(), skill.id(), skill.version());
        AgentResponse agent = agentService.createAgent(userA(), new AgentCreateRequest("Agent", "desc", "RAW", null, "prompt", null,
            List.of(new AgentSkillBindingRequest(skill.id(), 1)), List.of()));

        AgentConfigurationException disabled = assertThrows(AgentConfigurationException.class,
            () -> agentService.onlineAgent(userA(), agent.id(), agent.version()));
        assertEquals(MvpErrorCode.AGENT_INVALID_STATE, disabled.code());

        SkillResponse enabled = skillService.enableSkill(userA(), skill.id(), 1L);
        assertEquals("ENABLED", enabled.status());
        fakeToolBindingPort.setResolveResult(new ToolBindingView(List.of(), Map.of(), List.of("builtin:file")));
        AgentConfigurationException invalid = assertThrows(AgentConfigurationException.class,
            () -> agentService.onlineAgent(userA(), agent.id(), agent.version()));
        assertEquals(MvpErrorCode.TOOL_BINDING_INVALID, invalid.code());
    }
}
