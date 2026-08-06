package com.jd.genie.platform.phase2.configuration.agent.runtime;

import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2.configuration.agent.dto.AgentResponse;
import com.jd.genie.platform.phase2contract.dto.AgentCapabilitySummary;
import com.jd.genie.platform.phase2contract.error.Phase2ContractException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentRuntimeCatalogPortTest extends AgentRuntimeCatalogTestSupport {

    @Test
    void rejectsNullUserAndNullAllowedAgentIds() {
        Phase2ContractException nullUser = assertThrows(Phase2ContractException.class,
            () -> runtimeCatalogPort.listOnlineCandidates(null, List.of()));
        assertEquals(MvpErrorCode.VALIDATION_ERROR, nullUser.errorCode());

        Phase2ContractException nullWhitelist = assertThrows(Phase2ContractException.class,
            () -> runtimeCatalogPort.listOnlineCandidates(userA(), null));
        assertEquals(MvpErrorCode.VALIDATION_ERROR, nullWhitelist.errorCode());
    }

    @Test
    void listsOnlyOwnedOnlineCandidatesWithStableDatabaseOrder() {
        AgentResponse first = onlineAgent("First", List.of());
        AgentResponse second = onlineAgent("Second", List.of());
        draftAgent("Draft", List.of());
        offlineAgent("Offline");
        onlineAgent(userB(), "Other owner", List.of());

        List<String> ids = runtimeCatalogPort.listOnlineCandidates(userA(), List.of()).stream()
            .map(AgentCapabilitySummary::agentId)
            .toList();

        assertEquals(List.of(second.id(), first.id()), ids);
        assertFalse(ids.contains("Draft"));
    }

    @Test
    void whitelistReturnsVisibleOnlineIntersectionAndDoesNotLeakMissingIds() {
        AgentResponse visible = onlineAgent("Visible", List.of());
        AgentResponse otherOwner = onlineAgent(userB(), "Other", List.of());
        AgentResponse draft = draftAgent("Draft", List.of());

        List<AgentCapabilitySummary> result = runtimeCatalogPort.listOnlineCandidates(userA(),
            List.of(otherOwner.id(), visible.id(), visible.id(), draft.id(), "missing-agent"));

        assertEquals(1, result.size());
        assertEquals(visible.id(), result.get(0).agentId());
    }

    @Test
    void whitelistOverTwentyFailsBeforeQuerying() {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            ids.add("agent-" + i);
        }

        Phase2ContractException ex = assertThrows(Phase2ContractException.class,
            () -> runtimeCatalogPort.listOnlineCandidates(userA(), ids));
        assertEquals(MvpErrorCode.VALIDATION_ERROR, ex.errorCode());
    }

    @Test
    void blankWhitelistEntriesDoNotTurnIntoFullScan() {
        assertTrue(runtimeCatalogPort.listOnlineCandidates(userA(), List.of(" ", "")).isEmpty());
    }
}
