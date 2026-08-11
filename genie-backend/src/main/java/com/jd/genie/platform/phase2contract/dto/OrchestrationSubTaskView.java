package com.jd.genie.platform.phase2contract.dto;

public record OrchestrationSubTaskView(
    String subTaskId,
    String agentId,
    String agentName,
    String objective
) {
}
