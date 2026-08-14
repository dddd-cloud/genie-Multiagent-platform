package com.jd.genie.platform.phase2.runtime.plan;

import com.jd.genie.platform.phase2contract.enums.StepMode;

import java.util.List;

public record OrchestrationStep(
        String stepId,
        StepMode mode,
        String objective,
        List<String> inputRefs,
        String agentId,
        List<OrchestrationSubTask> subTasks
) {
    public OrchestrationStep {
        inputRefs = inputRefs == null ? List.of() : List.copyOf(inputRefs);
        subTasks = subTasks == null ? List.of() : List.copyOf(subTasks);
    }

    /** Maintains legacy internal plan construction as a single-agent step. */
    public OrchestrationStep(String stepId, String agentId, String objective, List<String> inputRefs) {
        this(stepId, StepMode.SINGLE_AGENT, objective, inputRefs, agentId, List.of());
    }
}
