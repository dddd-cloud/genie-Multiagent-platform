package com.jd.genie.platform.phase2contract.support;

import com.jd.genie.agent.agent.AgentContext;
import com.jd.genie.agent.tool.ToolCollection;
import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2contract.dto.AgentRuntimeProfile;
import com.jd.genie.platform.phase2contract.error.Phase2ContractException;
import com.jd.genie.platform.phase2contract.port.RuntimeToolCollectionPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Reusable contract suite for B runtime collection implementations and the C0 fake. */
public abstract class RuntimeToolCollectionPortContractTest {

    protected abstract RuntimeToolCollectionPort port();

    protected abstract CurrentUser currentUser();

    protected abstract AgentRuntimeProfile profile();

    protected abstract AgentContext context();

    protected void resetContractFixture() {
    }

    @BeforeEach
    protected final void resetRuntimeCollectionContractFixture() {
        resetContractFixture();
    }

    @Test
    final void nullArgumentsAreValidationErrors() {
        assertValidationError(() -> port().build(null, profile(), context()));
        assertValidationError(() -> port().build(currentUser(), null, context()));
        assertValidationError(() -> port().build(currentUser(), profile(), null));
    }

    @Test
    final void buildNeverReturnsNull() {
        ToolCollection collection = port().build(currentUser(), profile(), context());
        assertNotNull(collection);
    }

    private static void assertValidationError(Runnable invocation) {
        Phase2ContractException error = assertThrows(Phase2ContractException.class, invocation::run);
        assertEquals(MvpErrorCode.VALIDATION_ERROR, error.errorCode());
    }
}
