package com.jd.genie.platform.phase2.configuration.agent.runtime;

import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2.configuration.agent.dto.AgentResponse;
import com.jd.genie.platform.phase2contract.error.Phase2ContractException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentRuntimeCatalogIsolationTest extends AgentRuntimeCatalogTestSupport {

    @Test
    void loadOtherOwnerTenantDeletedAndMissingAgentsAsNotFound() {
        AgentResponse otherOwner = onlineAgent(userB(), "Other owner", List.of());
        AgentResponse otherTenant = onlineAgent(tenantBUser(), "Other tenant", List.of());
        AgentResponse deleted = offlineAgent("Deleted");
        agentService.deleteAgent(userA(), deleted.id(), deleted.version());

        assertNotFound(otherOwner.id());
        assertNotFound(otherTenant.id());
        assertNotFound(deleted.id());
        assertNotFound("missing-agent");
    }

    @Test
    void offlineOwnedAgentReportsOfflineWithoutLeakingProfile() {
        AgentResponse offline = offlineAgent("Offline");

        Phase2ContractException ex = assertThrows(Phase2ContractException.class,
            () -> runtimeCatalogPort.loadOnlineProfile(userA(), offline.id()));
        assertEquals(MvpErrorCode.AGENT_OFFLINE, ex.errorCode());
    }

    @Test
    void listCandidateSqlDoesNotReturnCrossOwnerOrCrossTenantRows() {
        onlineAgent("Visible", List.of());
        onlineAgent(userB(), "Other owner", List.of());
        onlineAgent(tenantBUser(), "Other tenant", List.of());

        assertEquals(1, runtimeCatalogPort.listOnlineCandidates(userA(), List.of()).size());
        assertTrue(runtimeCatalogPort.listOnlineCandidates(userB(), List.of()).stream()
            .allMatch(summary -> summary.name().contains("Other owner")));
    }

    private void assertNotFound(String agentId) {
        Phase2ContractException ex = assertThrows(Phase2ContractException.class,
            () -> runtimeCatalogPort.loadOnlineProfile(userA(), agentId));
        assertEquals(MvpErrorCode.RESOURCE_NOT_FOUND, ex.errorCode());
    }
}
