package com.jd.genie.platform.phase2.runtime.plan;

public record OrchestrationSubTask(
        String subTaskId,
        String agentId,
        String objective
) {
}
