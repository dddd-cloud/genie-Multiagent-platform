package com.jd.genie.platform.phase2.configuration.agent;

import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2.configuration.agent.dto.AgentCreateRequest;
import com.jd.genie.platform.phase2.configuration.agent.dto.AgentResponse;
import com.jd.genie.platform.phase2.configuration.agent.dto.AgentUpdateRequest;
import com.jd.genie.platform.phase2.configuration.agent.exception.AgentConfigurationException;
import com.jd.genie.platform.phase2.configuration.agent.service.AgentDefinitionService;
import com.jd.genie.platform.phase2.configuration.support.Phase2AMySqlTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentVersionConflictTest extends Phase2AMySqlTestSupport {

    @Autowired
    private AgentDefinitionService agentService;

    @Test
    void staleVersionReturnsVersionConflictAndDoesNotCallToolBinding() {
        AgentResponse created = agentService.createAgent(userA(), new AgentCreateRequest("Agent", "desc", null, null, "prompt", null, List.of(), List.of()));
        agentService.updateAgent(userA(), created.id(), new AgentUpdateRequest(0L, "Agent v2", "desc", null, null, "prompt", null, List.of(), List.of()));
        fakeToolBindingPort.reset();

        AgentConfigurationException error = assertThrows(AgentConfigurationException.class,
            () -> agentService.updateAgent(userA(), created.id(), new AgentUpdateRequest(0L, "Agent v3", "desc", null, null, "prompt", null, List.of(), List.of())));

        assertEquals(MvpErrorCode.VERSION_CONFLICT, error.code());
        assertTrue(fakeToolBindingPort.getCalls().isEmpty());
    }
}