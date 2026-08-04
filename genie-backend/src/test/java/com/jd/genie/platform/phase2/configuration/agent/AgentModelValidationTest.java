package com.jd.genie.platform.phase2.configuration.agent;

import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2.configuration.agent.dto.AgentCreateRequest;
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

class AgentModelValidationTest extends Phase2AMySqlTestSupport {

    @Autowired
    private AgentDefinitionService agentService;
    @Autowired
    private AgentDefinitionMapper agentMapper;

    @Test
    void rejectsUnavailableModelBeforeWritingAgentOrCallingToolBinding() {
        AgentConfigurationException ex = assertThrows(AgentConfigurationException.class,
            () -> agentService.createAgent(userA(), new AgentCreateRequest("Agent", "desc", "RAW", null,
                "prompt", "missing-model", List.of(), List.of())));

        assertEquals(MvpErrorCode.MODEL_NOT_AVAILABLE, ex.code());
        assertEquals(0, agentMapper.countOwned(userA().tenantId(), userA().userId()));
        assertTrue(fakeToolBindingPort.getCalls().isEmpty());
    }
}
