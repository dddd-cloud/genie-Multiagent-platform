package com.jd.genie.platform.phase2.configuration.agent;

import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2.configuration.agent.dto.AgentCreateRequest;
import com.jd.genie.platform.phase2.configuration.agent.dto.AgentResponse;
import com.jd.genie.platform.phase2.configuration.agent.entity.AgentDefinitionEntity;
import com.jd.genie.platform.phase2.configuration.agent.exception.AgentConfigurationException;
import com.jd.genie.platform.phase2.configuration.agent.mapper.AgentDefinitionMapper;
import com.jd.genie.platform.phase2.configuration.agent.service.AgentDefinitionService;
import com.jd.genie.platform.phase2.configuration.support.Phase2AMySqlTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentOnlinePromptRevalidationTest extends Phase2AMySqlTestSupport {

    @Autowired
    private AgentDefinitionService agentService;
    @Autowired
    private AgentDefinitionMapper agentMapper;

    @Test
    void onlineRevalidatesPromptBeforeToolBindingAndKeepsDraftOnFailure() {
        AgentResponse created = agentService.createAgent(userA(), new AgentCreateRequest("Agent", "desc", "RAW", null,
            "prompt", null, List.of(), List.of()));
        fakeToolBindingPort.reset();
        jdbcTemplate.update("UPDATE agent_definition SET system_prompt = ? WHERE id = ?", "{{password}}", created.id());

        AgentConfigurationException ex = assertThrows(AgentConfigurationException.class,
            () -> agentService.onlineAgent(userA(), created.id(), created.version()));

        AgentDefinitionEntity stored = agentMapper.selectOwnedById(userA().tenantId(), userA().userId(), created.id());
        assertEquals(MvpErrorCode.PROMPT_INVALID, ex.code());
        assertEquals("DRAFT", stored.getStatus());
        assertEquals(0L, stored.getVersion());
        assertTrue(fakeToolBindingPort.getCalls().isEmpty());
    }
}
