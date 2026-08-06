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

    @Test
    void dropsThoughtTracesBeforeCriticalTraceKindsWhenPruning() {
        GptProcessResult thought = GptProcessResult.builder()
                .status("running")
                .response("")
                .responseAll("")
                .finished(false)
                .packageType("orchestration_trace")
                .resultMap(Map.of("orchestrationTrace", Map.of(
                        "schemaVersion", 1,
                        "sequence", 1,
                        "kind", "THOUGHT",
                        "text", "x".repeat(2_000)
                )))
                .build();
        GptProcessResult output = GptProcessResult.builder()
                .status("running")
                .response("")
                .responseAll("")
                .finished(false)
                .packageType("orchestration_trace")
                .resultMap(Map.of("orchestrationTrace", Map.of(
                        "schemaVersion", 1,
                        "sequence", 2,
                        "kind", "OUTPUT",
                        "text", "step output"
                )))
                .build();
        GptProcessResult mainStatus = GptProcessResult.builder()
                .status("running")
                .response("")
                .responseAll("")
                .finished(false)
                .packageType("orchestration_trace")
                .resultMap(Map.of("orchestrationTrace", Map.of(
                        "schemaVersion", 1,
                        "sequence", 3,
                        "kind", "STATUS",
                        "text", "plan ready"
                )))
                .build();
        GptProcessResult finalResponse = mapper.finalResponse("request-1", "run-1", 4, "final answer", "SUCCESS");
        StreamSnapshotEnvelope source = new StreamSnapshotEnvelope(
                1, false, List.of(thought, output, mainStatus, finalResponse)
        );
        long maxBytes = pruner.utf8Size(new StreamSnapshotEnvelope(1, false, List.of(output, mainStatus, finalResponse))) + 32;

        StreamSnapshotEnvelope pruned = pruner.prune(source, maxBytes);

        assertTrue(pruned.truncated());
        assertEquals(List.of("OUTPUT", "STATUS", "FINAL_RESPONSE"), pruned.events().stream()
                .map(this::traceOrFinalKind)
                .toList());
    }

    private String eventType(GptProcessResult event) {
        Map<?, ?> details = (Map<?, ?>) event.getResultMap().get("orchestrationEvent");
        return details.get("eventType").toString();
    }

    private String traceOrFinalKind(GptProcessResult event) {
        if ("result".equals(event.getPackageType()) || Boolean.TRUE.equals(event.isFinished())) {
            Map<?, ?> details = (Map<?, ?>) event.getResultMap().get("orchestrationEvent");
            if (details != null && details.get("eventType") != null) {
                return details.get("eventType").toString();
            }
            return "FINAL_RESPONSE";
        }
        Map<?, ?> trace = (Map<?, ?>) event.getResultMap().get("orchestrationTrace");
        return trace.get("kind").toString();
    }
}
