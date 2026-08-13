package com.jd.genie.platform.agentbridge;

import com.jd.genie.model.response.GptProcessResult;
import com.jd.genie.platform.contract.StreamSnapshotEnvelope;
import com.jd.genie.platform.phase2contract.BrowserSkillExecutionContract;
import com.jd.genie.platform.phase2contract.dto.BrowserSkillExecutionSignal;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnapshotV1CompatibilityTest {

    private final SnapshotPruner pruner = new SnapshotPruner();

    @Test
    void pruningKeepsPayloadVersion1AndProtectsCriticalR3Events() {
        StreamSnapshotEnvelope snapshot = new StreamSnapshotEnvelope(
                1,
                false,
                List.of(
                        orchestrationEvent("PLAN_CREATED"),
                        skillExecutionPacket(),
                        thoughtTrace("long thought"),
                        hugeFillerEvent(),
                        orchestrationEvent("STEP_COMPLETED"),
                        finishedFinalResponse()
                )
        );

        long maxBytes = pruner.utf8Size(new StreamSnapshotEnvelope(1, false, List.of(
                orchestrationEvent("PLAN_CREATED"),
                orchestrationEvent("STEP_COMPLETED"),
                finishedFinalResponse()
        ))) + 96;
        StreamSnapshotEnvelope pruned = pruner.prune(snapshot, maxBytes);

        assertEquals(1, pruned.payloadVersion());
        assertTrue(pruned.truncated());
        List<?> keptTypes = pruned.events().stream()
                .map(event -> event.getResultMap() == null
                        ? Map.of()
                        : event.getResultMap().getOrDefault("orchestrationEvent", Map.of()))
                .map(value -> ((Map<?, ?>) value).get("eventType"))
                .toList();
        assertTrue(keptTypes.contains("PLAN_CREATED"));
        assertTrue(keptTypes.contains("STEP_COMPLETED"));
        assertTrue(keptTypes.contains("FINAL_RESPONSE"));
        // skill_execution and THOUGHT traces are transient and droppable.
        assertTrue(pruned.events().stream()
                .noneMatch(event -> "skill_execution".equals(event.getPackageType())));
        assertTrue(pruned.events().stream()
                .noneMatch(event -> "orchestration_trace".equals(event.getPackageType())));
        assertTrue(pruned.events().stream().anyMatch(GptProcessResult::isFinished));
        assertFalse(pruned.events().stream().anyMatch(event -> "huge-filler".equals(event.getPackageType())));
    }

    @Test
    void snapshotThatAlreadyFitsIsReturnedUntouched() {
        StreamSnapshotEnvelope snapshot = new StreamSnapshotEnvelope(
                1,
                false,
                List.of(finishedFinalResponse())
        );

        StreamSnapshotEnvelope pruned = pruner.prune(snapshot, SnapshotPruner.DEFAULT_MAX_BYTES);

        assertEquals(1, pruned.payloadVersion());
        assertFalse(pruned.truncated());
        assertEquals(1, pruned.events().size());
    }

    private GptProcessResult orchestrationEvent(String eventType) {
        return GptProcessResult.builder()
                .status("running")
                .response("")
                .responseAll("")
                .finished(false)
                .packageType("orchestration")
                .resultMap(Map.of("orchestrationEvent", Map.of(
                        "eventType", eventType,
                        "schemaVersion", 2,
                        "eventId", "request-1:1",
                        "sequence", 1
                )))
                .build();
    }

    private GptProcessResult skillExecutionPacket() {
        BrowserSkillExecutionSignal signal = new BrowserSkillExecutionSignal(
                BrowserSkillExecutionContract.SCHEMA_VERSION, "exec-1", "skill-1", "main", "hash-1", 30_000L
        );
        return GptProcessResult.builder()
                .status("running")
                .response("")
                .responseAll("")
                .finished(false)
                .packageType(BrowserSkillExecutionContract.SSE_PACKAGE_TYPE)
                .resultMap(Map.of(BrowserSkillExecutionContract.RESULT_MAP_KEY, signal))
                .build();
    }

    private GptProcessResult thoughtTrace(String text) {
        return GptProcessResult.builder()
                .status("running")
                .response("")
                .responseAll("")
                .finished(false)
                .packageType("orchestration_trace")
                .resultMap(Map.of("orchestrationTrace", Map.of("kind", "THOUGHT", "text", text)))
                .build();
    }

    private GptProcessResult hugeFillerEvent() {
        return GptProcessResult.builder()
                .status("running")
                .response("x".repeat(20_000))
                .responseAll("x".repeat(20_000))
                .finished(false)
                .packageType("huge-filler")
                .build();
    }

    private GptProcessResult finishedFinalResponse() {
        return GptProcessResult.builder()
                .status("success")
                .response("final answer")
                .responseAll("final answer")
                .finished(true)
                .packageType("result")
                .resultMap(Map.of("orchestrationEvent", Map.of(
                        "eventType", "FINAL_RESPONSE",
                        "schemaVersion", 2,
                        "eventId", "request-1:9",
                        "sequence", 9
                )))
                .build();
    }
}
