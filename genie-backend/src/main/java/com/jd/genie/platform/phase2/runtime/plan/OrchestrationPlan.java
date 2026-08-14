package com.jd.genie.platform.phase2.runtime.plan;

import java.util.List;

public record OrchestrationPlan(List<OrchestrationStep> steps) {
    public OrchestrationPlan {
        steps = steps == null ? List.of() : List.copyOf(steps);
    }
}
