package com.jd.genie.platform.phase2contract;

import com.jd.genie.model.response.GptProcessResult;
import com.jd.genie.platform.agentbridge.StreamSnapshotBuffer;
import com.jd.genie.platform.contract.StreamSnapshotEnvelope;
import com.jd.genie.platform.phase2contract.dto.OrchestrationEvent;
import com.jd.genie.platform.phase2contract.dto.OrchestrationPlanStepView;
import com.jd.genie.platform.phase2contract.enums.OrchestrationCompletionStatus;
import com.jd.genie.platform.phase2contract.enums.OrchestrationEventType;
import com.jd.genie.platform.phase2contract.enums.OrchestrationRoute;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Phase2SnapshotCompatibilityTest {

    @Test
    void orchestrationEventsEnterExistingSnapshotBufferWithPayloadVersion1() {
        StreamSnapshotBuffer buffer = new StreamSnapshotBuffer();

        assertTrue(buffer.append(orchestrationEvent(1, OrchestrationEventType.ROUTE_SELECTED, false,
            OrchestrationRoute.ORCHESTRATED, "MULTI_STEP", null, null, null, List.of(), null)));
        assertTrue(buffer.append(orchestrationEvent(2, OrchestrationEventType.PLAN_CREATED, false,
            null, null, 1, null, null,
            List.of(new OrchestrationPlanStepView("step-1", "agent-1", "Agent", "do", List.of())),
            null)));
        assertTrue(buffer.append(orchestrationEvent(3, OrchestrationEventType.STEP_COMPLETED, false,
            null, null, 1, "step-1", "agent-1", List.of(), null)));
        assertTrue(buffer.append(finalResponse(4, "Final answer body")));

        GptProcessResult heartbeat = GptProcessResult.builder()
            .status("success")
            .response("")
            .responseAll("")
            .finished(false)
            .packageType("heartbeat")
            .responseType("json")
            .traceId("trace-fixture")
            .reqId("request-fixture")
            .build();
        assertFalse(buffer.append(heartbeat));

        StreamSnapshotEnvelope snapshot = buffer.snapshot();
        assertEquals(1, snapshot.payloadVersion());
        assertFalse(snapshot.truncated());
        assertEquals(4, snapshot.events().size());
        assertEquals("orchestration", snapshot.events().get(0).getPackageType());
        assertFalse(snapshot.events().get(0).isFinished());
        assertEquals("result", snapshot.events().get(3).getPackageType());
        assertTrue(snapshot.events().get(3).isFinished());
        assertEquals("Final answer body", snapshot.events().get(3).getResponse());
        assertEquals(snapshot.events().get(3).getResponse(), snapshot.events().get(3).getResponseAll());
    }

    private static GptProcessResult orchestrationEvent(
        long sequence,
        OrchestrationEventType type,
        boolean finished,
        OrchestrationRoute route,
        String reasonCode,
        Integer attemptNo,
        String stepId,
        String agentId,
        List<OrchestrationPlanStepView> steps,
        OrchestrationCompletionStatus completionStatus
    ) {
        OrchestrationEvent event = new OrchestrationEvent(
            1,
            "request-fixture:" + sequence,
            sequence,
            type,
            "request-fixture",
            "run-fixture",
            attemptNo,
            stepId,
            agentId,
            agentId == null ? null : "Agent",
            route,
            reasonCode,
            null,
            steps,
            completionStatus
        );
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("orchestrationEvent", event);
        return GptProcessResult.builder()
            .status("success")
            .response("")
            .responseAll("")
            .finished(finished)
            .useTimes(0)
            .useTokens(0)
            .resultMap(resultMap)
            .responseType("json")
            .traceId("trace-fixture")
            .reqId("request-fixture")
            .encrypted(false)
            .packageType("orchestration")
            .build();
    }

    private static GptProcessResult finalResponse(long sequence, String text) {
        OrchestrationEvent event = new OrchestrationEvent(
            1,
            "request-fixture:" + sequence,
            sequence,
            OrchestrationEventType.FINAL_RESPONSE,
            "request-fixture",
            "run-fixture",
            1,
            null,
            null,
            null,
            OrchestrationRoute.ORCHESTRATED,
            null,
            null,
            List.of(),
            OrchestrationCompletionStatus.SUCCESS
        );
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("orchestrationEvent", event);
        return GptProcessResult.builder()
            .status("success")
            .response(text)
            .responseAll(text)
            .finished(true)
            .useTimes(1)
            .useTokens(10)
            .resultMap(resultMap)
            .responseType("markdown")
            .traceId("trace-fixture")
            .reqId("request-fixture")
            .encrypted(false)
            .packageType("result")
            .build();
    }
}
