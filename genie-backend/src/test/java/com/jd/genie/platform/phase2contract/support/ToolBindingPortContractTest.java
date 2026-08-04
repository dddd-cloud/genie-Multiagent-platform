package com.jd.genie.platform.phase2contract.support;

import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2contract.dto.ToolBindingView;
import com.jd.genie.platform.phase2contract.error.Phase2ContractException;
import com.jd.genie.platform.phase2contract.port.ToolBindingPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Reusable contract suite for B implementations and the C0 fake. */
public abstract class ToolBindingPortContractTest {

    protected abstract ToolBindingPort port();

    protected abstract CurrentUser currentUser();

    protected abstract String agentId();

    protected abstract String skillId();

    protected void resetContractFixture() {
    }

    @BeforeEach
    protected final void resetBindingContractFixture() {
        resetContractFixture();
    }

    @Test
    final void nullEnabledSkillIdsIsValidationError() {
        Phase2ContractException error = assertThrows(
            Phase2ContractException.class,
            () -> port().resolveBindings(currentUser(), agentId(), null)
        );
        assertEquals(MvpErrorCode.VALIDATION_ERROR, error.errorCode());
    }

    @Test
    final void resolveNeverReturnsNullCollections() {
        ToolBindingView view = port().resolveBindings(currentUser(), agentId(), List.of());
        assertNotNull(view);
        assertNotNull(view.directCapabilities());
        assertNotNull(view.skillCapabilities());
        assertNotNull(view.invalidCapabilities());
    }

    @Test
    final void nullAndDuplicateCapabilityKeysFailAtomically() {
        Phase2ContractException nullError = assertThrows(
            Phase2ContractException.class,
            () -> port().replaceAgentBindings(currentUser(), agentId(), null)
        );
        assertEquals(MvpErrorCode.VALIDATION_ERROR, nullError.errorCode());

        Phase2ContractException duplicateError = assertThrows(
            Phase2ContractException.class,
            () -> port().replaceSkillBindings(
                currentUser(),
                skillId(),
                List.of("builtin:file", "builtin:file")
            )
        );
        assertEquals(MvpErrorCode.TOOL_BINDING_INVALID, duplicateError.errorCode());
    }

    @Test
    final void emptyReplaceClearsAndRemoveIsIdempotent() {
        port().replaceAgentBindings(currentUser(), agentId(), List.of());
        port().replaceSkillBindings(currentUser(), skillId(), List.of());
        port().removeAgentBindings(currentUser(), agentId());
        port().removeAgentBindings(currentUser(), agentId());
        port().removeSkillBindings(currentUser(), skillId());
        port().removeSkillBindings(currentUser(), skillId());
    }
}
