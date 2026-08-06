package com.jd.genie.platform.phase2.runtime.plan;

import com.jd.genie.platform.agentbridge.AgentBridgeException;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2contract.dto.AgentCapabilitySummary;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrchestrationPlanValidatorTest {
    private final OrchestrationPlanValidator validator = new OrchestrationPlanValidator();
    private final List<AgentCapabilitySummary> candidates = List.of(
            new AgentCapabilitySummary("research", 1L, "Research", "research"),
            new AgentCapabilitySummary("writer", 1L, "Writer", "writing")
    );

    @Test
    void acceptsOnlyBackwardInputReferencesWithinCandidateSnapshot() {
        OrchestrationPlan plan = new OrchestrationPlan(List.of(
                new OrchestrationStep("research-step", "research", "Find facts", List.of()),
                new OrchestrationStep("writer-step", "writer", "Compose answer", List.of("research-step"))
        ));

        assertDoesNotThrow(() -> validator.validate(plan, candidates));
    }

    @Test
    void rejectsFutureReferenceBeforeAnyAgentCanStart() {
        OrchestrationPlan plan = new OrchestrationPlan(List.of(
                new OrchestrationStep("writer-step", "writer", "Compose answer", List.of("research-step")),
                new OrchestrationStep("research-step", "research", "Find facts", List.of())
        ));

        AgentBridgeException error = assertThrows(AgentBridgeException.class, () -> validator.validate(plan, candidates));

        assertEquals(MvpErrorCode.ORCHESTRATION_PLAN_INVALID, error.getErrorCode());
    }

    @Test
    void rejectsAgentsOutsideCandidateSnapshot() {
        OrchestrationPlan plan = new OrchestrationPlan(List.of(
                new OrchestrationStep("step-1", "outside", "Do not execute", List.of())
        ));

        AgentBridgeException error = assertThrows(AgentBridgeException.class, () -> validator.validate(plan, candidates));

        assertEquals(MvpErrorCode.ORCHESTRATION_PLAN_INVALID, error.getErrorCode());
    }
}
