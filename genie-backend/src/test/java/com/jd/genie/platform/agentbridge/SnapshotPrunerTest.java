package com.jd.genie.platform.agentbridge;

import com.jd.genie.model.response.GptProcessResult;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.contract.StreamSnapshotEnvelope;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnapshotPrunerTest {

    private final SnapshotPruner pruner = new SnapshotPruner();

    @Test
    void defaultLimitIsFrozenAtEightMebibytesAndActuallyPrunesAboveIt() {
        String oversized = "x".repeat((int) SnapshotPruner.DEFAULT_MAX_BYTES);
        StreamSnapshotEnvelope source = envelope(List.of(
                event("tool", oversized, false, oversized),
                event("result", "最终回答", true, "最终结构")
        ));

        assertEquals(8_388_608L, SnapshotPruner.DEFAULT_MAX_BYTES);
        assertTrue(pruner.utf8Size(source) > SnapshotPruner.DEFAULT_MAX_BYTES);

        StreamSnapshotEnvelope pruned = pruner.prune(source);

        assertTrue(pruned.truncated());
        assertEquals(SnapshotPruner.TRUNCATED_VALUE, pruned.events().get(0).getResponse());
        assertEquals("最终回答", pruned.events().get(1).getResponse());
        assertTrue(pruner.utf8Size(pruned) <= SnapshotPruner.DEFAULT_MAX_BYTES);
    }

    @Test
    void utf8SizeUsesBytesRatherThanJavaCharacterCount() {
        StreamSnapshotEnvelope ascii = envelope(List.of(event("tool", "aaa", false, "a")));
        StreamSnapshotEnvelope chinese = envelope(List.of(event("tool", "你你你", false, "你")));

        assertTrue(pruner.utf8Size(chinese) > pruner.utf8Size(ascii));
    }

    @Test
    void recursivelyTruncatesNonFinalStringsAndNeverMutatesFinalEvent() {
        String longValue = "工具日志".repeat(1_100);
        GptProcessResult plan = event("plan", longValue, false, longValue);
        GptProcessResult tool = event("tool", longValue, false, longValue);
        GptProcessResult finalEvent = event("result", "最终回答", true, longValue);
        StreamSnapshotEnvelope source = envelope(List.of(plan, tool, finalEvent));
        long maxBytes = pruner.utf8Size(source) - 1;

        StreamSnapshotEnvelope pruned = pruner.prune(source, maxBytes);

        assertTrue(pruned.truncated());
        assertEquals(3, pruned.events().size());
        assertEquals(SnapshotPruner.TRUNCATED_VALUE, pruned.events().get(0).getResponse());
        assertEquals(SnapshotPruner.TRUNCATED_VALUE, nestedPayload(pruned.events().get(0)));
        assertEquals(SnapshotPruner.TRUNCATED_VALUE, nestedPayload(pruned.events().get(1)));
        assertEquals("最终回答", pruned.events().get(2).getResponse());
        assertEquals(longValue, nestedPayload(pruned.events().get(2)));

        assertEquals(longValue, source.events().get(0).getResponse());
        assertEquals(longValue, nestedPayload(source.events().get(0)));
    }

    @Test
    void removesOldestNonCriticalEventWhilePreservingPlanTaskAndFinal() {
        GptProcessResult oldestTool = event("tool", "a".repeat(3_000), false, "short");
        GptProcessResult plan = event("plan", "计划", false, "short");
        GptProcessResult laterTool = event("tool", "b".repeat(3_000), false, "short");
        GptProcessResult finalEvent = event("result", "完成", true, "short");
        StreamSnapshotEnvelope source = envelope(List.of(oldestTool, plan, laterTool, finalEvent));
        StreamSnapshotEnvelope expectedAfterOneRemoval = envelope(List.of(plan, laterTool, finalEvent));

        StreamSnapshotEnvelope pruned = pruner.prune(source, pruner.utf8Size(expectedAfterOneRemoval));

        assertTrue(pruned.truncated());
        assertEquals(List.of("计划", "b".repeat(3_000), "完成"), pruned.events().stream()
                .map(GptProcessResult::getResponse)
                .toList());
        assertEquals("plan", messageType(pruned.events().get(0)));
        assertTrue(pruned.events().get(2).isFinished());
    }

    @Test
    void returnsOriginalShapeWhenSnapshotFits() {
        StreamSnapshotEnvelope source = envelope(List.of(event("result", "完成", true, "short")));

        StreamSnapshotEnvelope result = pruner.prune(source, pruner.utf8Size(source));

        assertFalse(result.truncated());
        assertEquals("完成", result.events().get(0).getResponse());
    }

    @Test
    void oversizedFinalEventUsesFrozenFailureCode() {
        StreamSnapshotEnvelope source = envelope(List.of(
                event("result", "z".repeat(5_000), true, "z".repeat(5_000))
        ));

        AgentBridgeException exception = assertThrows(
                AgentBridgeException.class,
                () -> pruner.prune(source, 512)
        );

        assertEquals(MvpErrorCode.SNAPSHOT_TOO_LARGE, exception.getErrorCode());
    }

    @Test
    void pruningAndSerializationAreDeterministic() {
        StreamSnapshotEnvelope source = envelope(List.of(
                event("tool", "x".repeat(5_000), false, "x".repeat(5_000)),
                event("result", "完成", true, "short")
        ));
        long maxBytes = pruner.utf8Size(source) - 1;

        assertEquals(pruner.serialize(source, maxBytes), pruner.serialize(source, maxBytes));
    }

    @Test
    void acceptsEmptySnapshotWithinLimit() {
        StreamSnapshotEnvelope result = pruner.prune(envelope(List.of()), 100);

        assertFalse(result.truncated());
        assertTrue(result.events().isEmpty());
    }

    @Test
    void rejectsNullEnvelopeAndEventsWhilePreservingNullEntries() {
        List<GptProcessResult> eventsWithNull = new ArrayList<>();
        eventsWithNull.add(null);
        eventsWithNull.add(event("result", "完成", true, "short"));

        AgentBridgeException nullEnvelope = assertThrows(
                AgentBridgeException.class,
                () -> pruner.prune(null, 1_000)
        );
        AgentBridgeException nullEvents = assertThrows(
                AgentBridgeException.class,
                () -> pruner.prune(new StreamSnapshotEnvelope(1, false, null), 1_000)
        );
        StreamSnapshotEnvelope normalized = pruner.prune(envelope(eventsWithNull), 1_000);

        assertEquals(MvpErrorCode.SNAPSHOT_INVALID, nullEnvelope.getErrorCode());
        assertEquals(MvpErrorCode.SNAPSHOT_INVALID, nullEvents.getErrorCode());
        assertEquals(2, normalized.events().size());
        assertNull(normalized.events().get(0));
        assertEquals("完成", normalized.events().get(1).getResponse());
    }

    @Test
    void rejectsInvalidEnvelopeAndLimit() {
        AgentBridgeException invalidVersion = assertThrows(
                AgentBridgeException.class,
                () -> pruner.prune(new StreamSnapshotEnvelope(2, false, List.of()), 100)
        );

        assertEquals(MvpErrorCode.SNAPSHOT_INVALID, invalidVersion.getErrorCode());
        assertThrows(IllegalArgumentException.class, () -> pruner.prune(envelope(List.of()), 0));
    }

    private StreamSnapshotEnvelope envelope(List<GptProcessResult> events) {
        return new StreamSnapshotEnvelope(1, false, events);
    }

    private GptProcessResult event(String messageType, String response, boolean finished, String nestedValue) {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("payload", nestedValue);
        Map<String, Object> eventData = new LinkedHashMap<>();
        eventData.put("messageType", messageType);
        eventData.put("resultMap", Map.of("items", List.of(nested)));
        Map<String, Object> resultMap = new LinkedHashMap<>();
        resultMap.put("eventData", eventData);

        return GptProcessResult.builder()
                .status(finished ? "success" : "running")
                .response(response)
                .responseAll("")
                .finished(finished)
                .resultMap(resultMap)
                .responseType("text")
                .packageType("result")
                .build();
    }

    private String messageType(GptProcessResult event) {
        return String.valueOf(((Map<?, ?>) event.getResultMap().get("eventData")).get("messageType"));
    }

    private String nestedPayload(GptProcessResult event) {
        Map<?, ?> eventData = (Map<?, ?>) event.getResultMap().get("eventData");
        Map<?, ?> resultMap = (Map<?, ?>) eventData.get("resultMap");
        List<?> items = (List<?>) resultMap.get("items");
        return String.valueOf(((Map<?, ?>) items.get(0)).get("payload"));
    }
}
