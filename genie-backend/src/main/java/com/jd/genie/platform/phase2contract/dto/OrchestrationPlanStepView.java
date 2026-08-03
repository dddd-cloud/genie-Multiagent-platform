package com.jd.genie.platform.phase2contract.dto;

import java.util.List;

public record OrchestrationPlanStepView(
    String stepId,
    String agentId,
    String agentName,
    String objective,
    List<String> inputRefs
) {
    public OrchestrationPlanStepView {
        inputRefs = inputRefs == null ? List.of() : List.copyOf(inputRefs);
    }
}
