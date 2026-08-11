package com.jd.genie.platform.phase2contract.dto;

import com.jd.genie.platform.phase2contract.enums.StepMode;

import java.util.List;

public record OrchestrationPlanStepView(
    String stepId,
    String agentId,
    String agentName,
    String objective,
    List<String> inputRefs,
    StepMode mode,
    List<OrchestrationSubTaskView> subTasks
) {
    public OrchestrationPlanStepView {
        inputRefs = inputRefs == null ? List.of() : List.copyOf(inputRefs);
        subTasks = subTasks == null ? List.of() : List.copyOf(subTasks);
    }

    /** Backward-compatible constructor for schemaVersion=1 callers. */
    public OrchestrationPlanStepView(
        String stepId,
        String agentId,
        String agentName,
        String objective,
        List<String> inputRefs
    ) {
        this(stepId, agentId, agentName, objective, inputRefs, null, List.of());
    }
}
