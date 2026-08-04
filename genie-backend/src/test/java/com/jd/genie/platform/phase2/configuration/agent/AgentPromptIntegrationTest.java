package com.jd.genie.platform.phase2.configuration.agent;

import com.jd.genie.platform.phase2.configuration.agent.dto.AgentCreateRequest;
import com.jd.genie.platform.phase2.configuration.agent.dto.AgentResponse;
import com.jd.genie.platform.phase2.configuration.agent.dto.AgentUpdateRequest;
import com.jd.genie.platform.phase2.configuration.agent.entity.AgentDefinitionEntity;
import com.jd.genie.platform.phase2.configuration.agent.mapper.AgentDefinitionMapper;
import com.jd.genie.platform.phase2.configuration.agent.service.AgentDefinitionService;
import com.jd.genie.platform.phase2.configuration.support.Phase2AMySqlTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentPromptIntegrationTest extends Phase2AMySqlTestSupport {

    @Autowired
    private AgentDefinitionService agentService;
    @Autowired
    private AgentDefinitionMapper agentMapper;

    @Test
    void structuredCreateAndUpdateCompilePromptAndIgnoreForgedFrontendSystemPrompt() {
        AgentResponse created = agentService.createAgent(userA(), new AgentCreateRequest("Agent", "desc", "STRUCTURED",
            "{\"objective\":\"Real objective\",\"role\":\"Researcher\"}", "FORGED_FRONTEND_COMPILED_TEXT", "system-default", List.of(), List.of()));

        AgentDefinitionEntity stored = agentMapper.selectOwnedById(userA().tenantId(), userA().userId(), created.id());
        assertTrue(stored.getSystemPrompt().contains("Real objective"));
        assertFalse(stored.getSystemPrompt().contains("FORGED_FRONTEND_COMPILED_TEXT"));
        assertTrue(stored.getPromptConfig().startsWith("{\"role\""));
        assertNull(stored.getModelName());

        AgentResponse updated = agentService.updateAgent(userA(), created.id(), new AgentUpdateRequest(created.version(),
            "Agent", "desc", "RAW", "{\"objective\":\"ignored\"}", "Raw user prompt", "qwen-max", List.of(), List.of()));

        stored = agentMapper.selectOwnedById(userA().tenantId(), userA().userId(), updated.id());
        assertNull(stored.getPromptConfig());
        assertTrue(stored.getSystemPrompt().contains("Raw user prompt"));
        assertTrue(stored.getSystemPrompt().contains("# Platform Execution Boundary"));
        assertTrue("qwen-max".equals(stored.getModelName()));
    }
}
