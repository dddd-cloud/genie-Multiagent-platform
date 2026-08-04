package com.jd.genie.platform.phase2contract.support;

import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2contract.dto.AgentCapabilitySummary;
import com.jd.genie.platform.phase2contract.dto.AgentRuntimeProfile;
import com.jd.genie.platform.phase2contract.error.Phase2ContractException;
import com.jd.genie.platform.phase2contract.port.AgentRuntimeCatalogPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Reusable contract suite for A implementations and the C0 fake.
 * Implementations supply one ONLINE agent owned by {@link #currentUser()}.
 */
public abstract class AgentRuntimeCatalogPortContractTest {

    protected abstract AgentRuntimeCatalogPort port();

    protected abstract CurrentUser currentUser();

    protected abstract String onlineAgentId();

    protected void resetContractFixture() {
    }

    @BeforeEach
    protected final void resetCatalogContractFixture() {
        resetContractFixture();
    }

    @Test
    final void nullWhitelistIsValidationError() {
        Phase2ContractException error = assertThrows(
            Phase2ContractException.class,
            () -> port().listOnlineCandidates(currentUser(), null)
        );
        assertEquals(MvpErrorCode.VALIDATION_ERROR, error.errorCode());
    }

    @Test
    final void candidateResultsAreNonNullAndImmutable() {
        List<AgentCapabilitySummary> candidates = port().listOnlineCandidates(
            currentUser(),
            List.of()
        );
        assertNotNull(candidates);
        assertFalse(candidates.isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> candidates.add(candidates.get(0)));
    }

    @Test
    final void blankAgentIdIsValidationError() {
        Phase2ContractException error = assertThrows(
            Phase2ContractException.class,
            () -> port().loadOnlineProfile(currentUser(), "  ")
        );
        assertEquals(MvpErrorCode.VALIDATION_ERROR, error.errorCode());
    }

    @Test
    final void onlineProfileIsNonNullAndHasImmutableCollections() {
        AgentRuntimeProfile profile = port().loadOnlineProfile(currentUser(), onlineAgentId());
        assertNotNull(profile);
        assertNotNull(profile.skills());
        assertNotNull(profile.capabilityKeys());
        assertThrows(UnsupportedOperationException.class, () -> profile.capabilityKeys().add("builtin:file"));
    }
}
