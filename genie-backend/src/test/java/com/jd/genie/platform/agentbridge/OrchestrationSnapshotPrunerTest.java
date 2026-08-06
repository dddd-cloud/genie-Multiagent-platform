package com.jd.genie.platform.agentbridge;

import com.jd.genie.model.response.GptProcessResult;
import com.jd.genie.platform.contract.StreamSnapshotEnvelope;
import com.jd.genie.platform.phase2.runtime.event.OrchestrationEventMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrchestrationSnapshotPrunerTest {
    private final SnapshotPruner pruner = new SnapshotPruner();
    private final OrchestrationEventMapper mapper = new OrchestrationEventMapper();

    @Test
    void preservesCriticalOrchestrationEventsAndTheFinalResponseInSnapshotV1() {
        GptProcessResult plan = mapper.progress("request-1", "run-1", 1, "PLAN_CREATED", Map.of(), List.of());
        GptProcessResult completed = mapper.progress("request-1", "run-1", 2, "STEP_COMPLETED", Map.of(), List.of());
        GptProcessResult nonCriticalTool = GptProcessResult.builder()
                .status("running")
                .response("x".repeat(5_000))
                .responseAll("")
                .finished(false)
                .packageType("tool")
                .resultMap(Map.of("eventData", Map.of("messageType", "tool")))
                .build();
        GptProcessResult finalResponse = mapper.finalResponse("request-1", "run-1", 3, "final answer", "SUCCESS");
        StreamSnapshotEnvelope source = new StreamSnapshotEnvelope(
                1, false, List.of(plan, completed, nonCriticalTool, finalResponse)
        );
        long maxBytes = pruner.utf8Size(new StreamSnapshotEnvelope(1, false, List.of(plan, completed, finalResponse))) + 64;

        StreamSnapshotEnvelope pruned = pruner.prune(source, maxBytes);

        assertEquals(1, pruned.payloadVersion());
        assertTrue(pruned.truncated());
        assertEquals(List.of("PLAN_CREATED", "STEP_COMPLETED", "FINAL_RESPONSE"), pruned.events().stream()
                .map(this::eventType)
                .toList());
        GptProcessResult terminal = pruned.events().get(2);
        assertTrue(terminal.isFinished());
        assertEquals("final answer", terminal.getResponseAll());
    }

    private String eventType(GptProcessResult event) {
        Map<?, ?> details = (Map<?, ?>) event.getResultMap().get("orchestrationEvent");
        return details.get("eventType").toString();
    }
}
