package com.jd.genie.platform.phase2contract.dto;

import com.jd.genie.platform.phase2contract.enums.AgentTaskErrorCode;
import com.jd.genie.platform.phase2contract.enums.OrchestrationCompletionStatus;
import com.jd.genie.platform.phase2contract.enums.OrchestrationEventType;
import com.jd.genie.platform.phase2contract.enums.OrchestrationRoute;
import com.jd.genie.platform.phase2contract.enums.StepMode;

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
    OrchestrationCompletionStatus completionStatus,
    String subTaskId,
    StepMode stepMode,
    Integer retryNo
) {
    public OrchestrationEvent {
        steps = steps == null ? List.of() : List.copyOf(steps);
    }

    /** Backward-compatible constructor for schemaVersion=1 callers. */
    public OrchestrationEvent(
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
        this(
            schemaVersion,
            eventId,
            sequence,
            eventType,
            requestId,
            runId,
            attemptNo,
            stepId,
            agentId,
            agentName,
            route,
            reasonCode,
            errorCode,
            steps,
            completionStatus,
            null,
            null,
            null
        );
    }
}
