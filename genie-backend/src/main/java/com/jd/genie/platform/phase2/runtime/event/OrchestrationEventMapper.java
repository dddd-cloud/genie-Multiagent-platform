package com.jd.genie.platform.phase2.runtime.event;

import com.jd.genie.model.response.GptProcessResult;
import com.jd.genie.platform.phase2contract.dto.OrchestrationPlanStepView;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class OrchestrationEventMapper {
    private static final String PACKAGE_TYPE = "orchestration";

    /**
     * Builds V2 event with support for stepMode, subTaskId, and retryNo.
     * schemaVersion=2 indicates new orchestration runtime with local retry and fallback.
     */
    public GptProcessResult progress(
            String requestId,
            String runId,
            long sequence,
            String eventType,
            Map<String, Object> details,
            List<OrchestrationPlanStepView> steps
    ) {
        Map<String, Object> normalizedDetails = details == null ? Map.of() : details;
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("schemaVersion", 2);
        event.put("eventId", requestId + ":" + sequence);
        event.put("sequence", sequence);
        event.put("eventType", eventType);
        event.put("requestId", requestId);
        event.put("runId", runId);
        event.put("attemptNo", normalizedDetails.get("attemptNo"));
        event.put("stepId", normalizedDetails.get("stepId"));
        event.put("stepMode", normalizedDetails.get("stepMode"));
        event.put("subTaskId", normalizedDetails.get("subTaskId"));
        event.put("retryNo", normalizedDetails.get("retryNo"));
        event.put("agentId", normalizedDetails.get("agentId"));
        event.put("agentName", normalizedDetails.get("agentName"));
        event.put("route", normalizedDetails.get("route"));
        event.put("reasonCode", normalizedDetails.get("reasonCode"));
        event.put("errorCode", normalizedDetails.get("errorCode"));
        event.put("steps", steps == null ? List.of() : List.copyOf(steps));
        event.put("completionStatus", normalizedDetails.get("completionStatus"));
        return GptProcessResult.builder()
                .status("running")
                .response("")
                .responseAll("")
                .finished(false)
                .packageType(PACKAGE_TYPE)
                .resultMap(Map.of("orchestrationEvent", event))
                .build();
    }

    /** Compatibility projection for callers that have not assigned a run yet. */
    public GptProcessResult progress(String requestId, long sequence, String eventType, Map<String, Object> details) {
        return progress(requestId, requestId, sequence, eventType, details, List.of());
    }

    public GptProcessResult finalResponse(
            String requestId,
            String runId,
            long sequence,
            String response,
            String completionStatus
    ) {
        return finalResponse(requestId, runId, sequence, response, completionStatus, List.of());
    }

    public GptProcessResult finalResponse(
            String requestId,
            String runId,
            long sequence,
            String response,
            String completionStatus,
            List<Map<String, Object>> fileList
    ) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("schemaVersion", 2);
        event.put("eventId", requestId + ":" + sequence);
        event.put("sequence", sequence);
        event.put("eventType", "FINAL_RESPONSE");
        event.put("requestId", requestId);
        event.put("runId", runId);
        event.put("attemptNo", null);
        event.put("stepId", null);
        event.put("stepMode", null);
        event.put("subTaskId", null);
        event.put("retryNo", null);
        event.put("agentId", null);
        event.put("agentName", null);
        event.put("route", null);
        event.put("reasonCode", null);
        event.put("errorCode", null);
        event.put("steps", List.of());
        event.put("completionStatus", completionStatus);
        Map<String, Object> resultMap = new LinkedHashMap<>();
        resultMap.put("orchestrationEvent", event);
        if (fileList != null && !fileList.isEmpty()) {
            resultMap.put("fileList", List.copyOf(fileList));
        }
        return GptProcessResult.builder()
                .status("success")
                .response(response)
                .responseAll(response)
                .finished(true)
                .packageType("result")
                .resultMap(resultMap)
                .build();
    }
}
