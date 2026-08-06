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
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentOwnershipIsolationTest extends Phase2AMySqlTestSupport {

    @Autowired
    private AgentDefinitionService agentService;

    @Test
    void crossOwnerAndTenantRequestsReturnNotFoundAndDoNotCallToolBinding() {
        AgentResponse created = agentService.createAgent(userA(), new AgentCreateRequest("Agent", "desc", "RAW", null, "prompt", null, List.of(), List.of()));
        fakeToolBindingPort.reset();

        AgentConfigurationException ownerError = assertThrows(AgentConfigurationException.class,
            () -> agentService.updateAgent(userB(), created.id(), new com.jd.genie.platform.phase2.configuration.agent.dto.AgentUpdateRequest(0L, "x", "desc", null, null, "prompt", null, List.of(), List.of())));
        AgentConfigurationException tenantError = assertThrows(AgentConfigurationException.class,
            () -> agentService.getAgent(tenantBUser(), created.id()));

        assertEquals(MvpErrorCode.RESOURCE_NOT_FOUND, ownerError.code());
        assertEquals(MvpErrorCode.RESOURCE_NOT_FOUND, tenantError.code());
        assertTrue(fakeToolBindingPort.getCalls().isEmpty());
    }
}
