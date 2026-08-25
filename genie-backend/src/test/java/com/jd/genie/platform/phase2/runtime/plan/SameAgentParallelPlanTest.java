package com.jd.genie.platform.phase2.runtime.plan;

import com.jd.genie.platform.agentbridge.AgentBridgeException;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2contract.dto.AgentCapabilitySummary;
import com.jd.genie.platform.phase2contract.enums.StepMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SameAgentParallelPlanTest {

    private static final List<AgentCapabilitySummary> CANDIDATES = List.of(
            new AgentCapabilitySummary("agent-a", 1L, "Agent A", "analysis"),
            new AgentCapabilitySummary("agent-b", 1L, "Agent B", "analysis")
    );

    private final OrchestrationPlanValidator validator = new OrchestrationPlanValidator();

    @Test
    void rejectsTheSameAgentAcrossDifferentSubTasksWithinOneParallelGroup() {
        OrchestrationPlan plan = new OrchestrationPlan(List.of(
                new OrchestrationStep(
                        "parallel",
                        StepMode.PARALLEL_AGENTS,
                        "compare independent angles",
                        List.of(),
                        null,
                        List.of(
                                new OrchestrationSubTask("sub-a", "agent-a", "angle A"),
                                new OrchestrationSubTask("sub-b", "agent-a", "angle B")
                        )
                )
        ));

        AgentBridgeException error = assertThrows(
                AgentBridgeException.class,
                () -> validator.validate(plan, CANDIDATES)
        );
        assertEquals(MvpErrorCode.ORCHESTRATION_PLAN_INVALID, error.getErrorCode());
    }

    @Test
    void rejectsDuplicateSubTaskIdsAcrossTheWholePlanBeforeExecution() {
        OrchestrationPlan plan = new OrchestrationPlan(List.of(
                new OrchestrationStep(
                        "parallel-1",
                        StepMode.PARALLEL_AGENTS,
                        "first group",
                        List.of(),
                        null,
                        List.of(
                                new OrchestrationSubTask("sub-x", "agent-a", "angle A"),
                                new OrchestrationSubTask("sub-y", "agent-b", "angle B")
                        )
                ),
                new OrchestrationStep(
                        "parallel-2",
                        StepMode.PARALLEL_AGENTS,
                        "second group",
                        List.of("parallel-1"),
                        null,
                        List.of(
                                new OrchestrationSubTask("sub-x", "agent-a", "duplicate id"),
                                new OrchestrationSubTask("sub-z", "agent-b", "angle C")
                        )
                )
        ));

        AgentBridgeException error = assertThrows(
                AgentBridgeException.class,
                () -> validator.validate(plan, CANDIDATES)
        );
        assertEquals(MvpErrorCode.ORCHESTRATION_PLAN_INVALID, error.getErrorCode());
    }

    @Test
    void rejectsSubTaskAgentsOutsideTheCandidateSnapshot() {
        OrchestrationPlan plan = new OrchestrationPlan(List.of(
                new OrchestrationStep(
                        "parallel",
                        StepMode.PARALLEL_AGENTS,
                        "compare independent angles",
                        List.of(),
                        null,
                        List.of(
                                new OrchestrationSubTask("sub-a", "agent-a", "angle A"),
                                new OrchestrationSubTask("sub-b", "ghost-agent", "angle B")
                        )
                )
        ));

        AgentBridgeException error = assertThrows(
                AgentBridgeException.class,
                () -> validator.validate(plan, CANDIDATES)
        );
        assertEquals(MvpErrorCode.ORCHESTRATION_PLAN_INVALID, error.getErrorCode());
    }
}
