package com.jd.genie.platform.phase2.configuration.agent;

import com.jd.genie.platform.contract.PageResponse;
import com.jd.genie.platform.phase2.configuration.agent.dto.AgentCreateRequest;
import com.jd.genie.platform.phase2.configuration.agent.dto.AgentResponse;
import com.jd.genie.platform.phase2.configuration.agent.dto.AgentUpdateRequest;
import com.jd.genie.platform.phase2.configuration.agent.service.AgentDefinitionService;
import com.jd.genie.platform.phase2.configuration.support.Phase2AMySqlTestSupport;
import com.jd.genie.platform.phase2contract.capability.CapabilityKeys;
import com.jd.genie.platform.phase2contract.support.FakeToolBindingPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentDefinitionServiceTest extends Phase2AMySqlTestSupport {

    @Autowired
    private AgentDefinitionService agentService;

    @Test
    void createsListsGetsAndUpdatesAgentWithoutChangingStatus() {
        AgentResponse created = agentService.createAgent(userA(), agentRequest("Research", List.of(CapabilityKeys.BUILTIN_FILE, CapabilityKeys.BUILTIN_FILE)));
        assertEquals("DRAFT", created.status());
        assertEquals("RAW", created.promptMode());
        assertEquals(List.of(CapabilityKeys.BUILTIN_FILE), created.capabilityKeys());

        PageResponse<AgentResponse> page = agentService.listAgents(userA(), 1, 1);
        assertEquals(1, page.items().size());
        assertFalse(page.hasMore());
        assertEquals(created.id(), agentService.getAgent(userA(), created.id()).id());

        AgentResponse updated = agentService.updateAgent(userA(), created.id(), new AgentUpdateRequest(
            created.version(), "Research v2", "updated", "RAW", null, "updated prompt", null, List.of(), List.of()));
        assertEquals("Research v2", updated.name());
        assertEquals("DRAFT", updated.status());
        assertEquals(1L, updated.version());
        assertTrue(fakeToolBindingPort.getCalls().stream()
            .anyMatch(call -> call.type() == FakeToolBindingPort.CallType.REPLACE_AGENT_BINDINGS));
    }

    private AgentCreateRequest agentRequest(String name, List<String> capabilityKeys) {
        return new AgentCreateRequest(name, "description", "RAW", null, "system prompt", null, List.of(), capabilityKeys);
    }
}
