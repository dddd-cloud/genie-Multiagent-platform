package com.jd.genie.platform.phase2.runtime.plan;

import com.jd.genie.platform.agentbridge.AgentBridgeException;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2contract.dto.AgentCapabilitySummary;
import com.jd.genie.platform.phase2contract.enums.StepMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlanStepModeValidationTest {
    private final OrchestrationPlanValidator validator = new OrchestrationPlanValidator();
    private final List<AgentCapabilitySummary> candidates = List.of(
            new AgentCapabilitySummary("research", 1L, "Research", "research"),
            new AgentCapabilitySummary("writer", 1L, "Writer", "writing")
    );

    @Test
    void acceptsAllThreeModesAndSameAgentInSeparateParallelSubTasks() {
        OrchestrationPlan plan = new OrchestrationPlan(List.of(
                new OrchestrationStep("main", StepMode.MAIN_ONLY, "Prepare scope", List.of(), null, List.of()),
                new OrchestrationStep("single", StepMode.SINGLE_AGENT, "Gather facts", List.of("main"), "research", List.of()),
                new OrchestrationStep("parallel", StepMode.PARALLEL_AGENTS, "Compare findings", List.of("single"), null, List.of(
                        new OrchestrationSubTask("compare-source-a", "research", "Evaluate source A"),
                        new OrchestrationSubTask("compare-source-b", "research", "Evaluate source B")
                ))
        ));

        assertDoesNotThrow(() -> validator.validate(plan, candidates));
    }

    @Test
    void rejectsModeFieldCombinationsOutsideTheFrozenShape() {
        assertPlanInvalid(new OrchestrationPlan(List.of(
                new OrchestrationStep("main", StepMode.MAIN_ONLY, "Prepare scope", List.of(), "research", List.of())
        )));
        assertPlanInvalid(new OrchestrationPlan(List.of(
                new OrchestrationStep("single", StepMode.SINGLE_AGENT, "Gather facts", List.of(), "research", List.of(
                        new OrchestrationSubTask("unexpected", "writer", "Unexpected work")
                ))
        )));
        assertPlanInvalid(new OrchestrationPlan(List.of(
                new OrchestrationStep("parallel", StepMode.PARALLEL_AGENTS, "Compare findings", List.of(), null, List.of(
                        new OrchestrationSubTask("only-one", "research", "Evaluate source A")
                ))
        )));
    }

    @Test
    void rejectsInvalidParallelSubTasksBeforeExecutionCanStart() {
        assertPlanInvalid(new OrchestrationPlan(List.of(
                new OrchestrationStep("parallel", StepMode.PARALLEL_AGENTS, "Compare findings", List.of(), null, List.of(
                        new OrchestrationSubTask("duplicate", "research", "Evaluate source A"),
                        new OrchestrationSubTask("duplicate", "writer", "Evaluate source B")
                ))
        )));
        assertPlanInvalid(new OrchestrationPlan(List.of(
                new OrchestrationStep("parallel", StepMode.PARALLEL_AGENTS, "Compare findings", List.of(), null, List.of(
                        new OrchestrationSubTask("source-a", "outside", "Evaluate source A"),
                        new OrchestrationSubTask("source-b", "writer", "Evaluate source B")
                ))
        )));
    }

    @Test
    void rejectsDuplicateTopLevelIdsAndInputReferences() {
        assertPlanInvalid(new OrchestrationPlan(List.of(
                new OrchestrationStep("duplicate", StepMode.MAIN_ONLY, "Prepare scope", List.of(), null, List.of()),
                new OrchestrationStep("duplicate", StepMode.SINGLE_AGENT, "Gather facts", List.of(), "research", List.of())
        )));
        assertPlanInvalid(new OrchestrationPlan(List.of(
                new OrchestrationStep("main", StepMode.MAIN_ONLY, "Prepare scope", List.of(), null, List.of()),
                new OrchestrationStep("single", StepMode.SINGLE_AGENT, "Gather facts", List.of("main", "main"), "research", List.of())
        )));
    }

    @Test
    void rejectsMoreThanFourParallelSubTasks() {
        assertPlanInvalid(new OrchestrationPlan(List.of(
                new OrchestrationStep("parallel", StepMode.PARALLEL_AGENTS, "Compare findings", List.of(), null, List.of(
                        new OrchestrationSubTask("source-a", "research", "Evaluate source A"),
                        new OrchestrationSubTask("source-b", "writer", "Evaluate source B"),
                        new OrchestrationSubTask("source-c", "research", "Evaluate source C"),
                        new OrchestrationSubTask("source-d", "writer", "Evaluate source D"),
                        new OrchestrationSubTask("source-e", "research", "Evaluate source E")
                ))
        )));
    }

    @Test
    void rejectsDuplicateSubTaskIdsAcrossTheWholePlan() {
        OrchestrationPlan plan = new OrchestrationPlan(List.of(
                parallelStep("parallel-a", "shared-a", "shared-b"),
                parallelStep("parallel-b", "shared-a", "shared-c")
        ));

        assertPlanInvalid(plan);
    }

    private OrchestrationStep parallelStep(String stepId, String firstSubTaskId, String secondSubTaskId) {
        return new OrchestrationStep(stepId, StepMode.PARALLEL_AGENTS, "Compare findings", List.of(), null, List.of(
                new OrchestrationSubTask(firstSubTaskId, "research", "Evaluate source A"),
                new OrchestrationSubTask(secondSubTaskId, "writer", "Evaluate source B")
        ));
    }

    private void assertPlanInvalid(OrchestrationPlan plan) {
        AgentBridgeException error = assertThrows(AgentBridgeException.class, () -> validator.validate(plan, candidates));
        assertEquals(MvpErrorCode.ORCHESTRATION_PLAN_INVALID, error.getErrorCode());
    }
}
