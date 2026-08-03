package com.jd.genie.platform.phase2contract.dto;

import com.jd.genie.platform.phase2contract.enums.AgentTaskErrorCode;
import com.jd.genie.platform.phase2contract.enums.OrchestrationCompletionStatus;
import com.jd.genie.platform.phase2contract.enums.OrchestrationEventType;
import com.jd.genie.platform.phase2contract.enums.OrchestrationRoute;

import java.util.List;

public record OrchestrationEvent(
    int schemaVersion,
    String eventId,
    long sequence,
    OrchestrationEventType eventType,
    String requestId,
    String runId,
    Integer attemptNo,
    String stepId,
    String agentId,
    String agentName,
    OrchestrationRoute route,
    String reasonCode,
    AgentTaskErrorCode errorCode,
    List<OrchestrationPlanStepView> steps,
    OrchestrationCompletionStatus completionStatus
) {
    public OrchestrationEvent {
        steps = steps == null ? List.of() : List.copyOf(steps);
    }
}
