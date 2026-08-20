package com.jd.genie.platform.phase2.configuration.agent;

import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2.configuration.agent.dto.AgentCreateRequest;
import com.jd.genie.platform.phase2.configuration.agent.dto.AgentResponse;
import com.jd.genie.platform.phase2.configuration.agent.exception.AgentConfigurationException;
import com.jd.genie.platform.phase2.configuration.agent.service.AgentDefinitionService;
import com.jd.genie.platform.phase2.configuration.support.Phase2AMySqlTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentStateTransitionTest extends Phase2AMySqlTestSupport {

    @Autowired
    private AgentDefinitionService agentService;

    @Test
    void supportsFrozenAgentStateTransitions() {
        AgentResponse draft = agentService.createAgent(userA(), new AgentCreateRequest("Agent", "desc", "RAW", null, "prompt", null, List.of(), List.of()));
        AgentResponse online = agentService.onlineAgent(userA(), draft.id(), draft.version());
        assertEquals("ONLINE", online.status());
        assertEquals(1L, online.version());
        AgentConfigurationException repeatOnline = assertThrows(AgentConfigurationException.class,
            () -> agentService.onlineAgent(userA(), draft.id(), online.version()));
        assertEquals(MvpErrorCode.AGENT_INVALID_STATE, repeatOnline.code());
        AgentResponse offline = agentService.offlineAgent(userA(), draft.id(), online.version());
        assertEquals("OFFLINE", offline.status());
        AgentResponse stillOffline = agentService.offlineAgent(userA(), draft.id(), offline.version());
        assertEquals(offline.version(), stillOffline.version());
    }

    @Test
    void deletesOnlineAgentWithoutOffline() {
        AgentResponse draft = agentService.createAgent(userA(), new AgentCreateRequest("Online Delete", "desc", "RAW", null, "prompt", null, List.of(), List.of()));
        AgentResponse online = agentService.onlineAgent(userA(), draft.id(), draft.version());
        assertEquals("ONLINE", online.status());
        agentService.deleteAgent(userA(), online.id(), online.version());
        AgentConfigurationException missing = assertThrows(AgentConfigurationException.class,
            () -> agentService.getAgent(userA(), online.id()));
        assertEquals(MvpErrorCode.RESOURCE_NOT_FOUND, missing.code());
    }
}
