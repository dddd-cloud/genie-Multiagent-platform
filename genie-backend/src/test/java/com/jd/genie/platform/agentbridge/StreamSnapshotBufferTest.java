package com.jd.genie.platform.agentbridge;

import com.jd.genie.model.response.GptProcessResult;
import com.jd.genie.platform.contract.StreamSnapshotEnvelope;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StreamSnapshotBufferTest {

    @Test
    void ignoresHeartbeatAndPreservesBusinessEventOrder() {
        StreamSnapshotBuffer buffer = new StreamSnapshotBuffer();
        GptProcessResult first = event("first", "result");
        GptProcessResult heartbeat = event("heartbeat", "heartbeat");
        GptProcessResult second = event("second", "result");

        assertTrue(buffer.append(first));
        assertFalse(buffer.append(heartbeat));
        assertTrue(buffer.append(second));

        StreamSnapshotEnvelope snapshot = buffer.snapshot();
        assertEquals(1, snapshot.payloadVersion());
        assertFalse(snapshot.truncated());
        assertEquals(List.of("first", "second"), snapshot.events().stream()
                .map(GptProcessResult::getResponse)
                .toList());
    }

    @Test
    void snapshotsOwnCopiesInsteadOfCallerMutableObjects() {
        StreamSnapshotBuffer buffer = new StreamSnapshotBuffer();
        GptProcessResult source = event("original", "result");
        source.setResultMap(Map.of("eventData", Map.of("result", "original")));

        buffer.append(source);
        source.setResponse("changed");
        source.setResultMap(Map.of("eventData", Map.of("result", "changed")));

        GptProcessResult buffered = buffer.events().get(0);
        assertEquals("original", buffered.getResponse());
        assertEquals("original", ((Map<?, ?>) buffered.getResultMap().get("eventData")).get("result"));
    }

    @Test
    void clearStartsANewEmptySnapshot() {
        StreamSnapshotBuffer buffer = new StreamSnapshotBuffer();
        buffer.append(event("one", "result"));

        buffer.clear();

        assertEquals(0, buffer.size());
        assertTrue(buffer.snapshot().events().isEmpty());
    }

    @Test
    void nullEventsAreRejected() {
        StreamSnapshotBuffer buffer = new StreamSnapshotBuffer();
        assertThrows(IllegalArgumentException.class, () -> buffer.append(null));
    }

    private GptProcessResult event(String response, String packageType) {
        return GptProcessResult.builder()
                .status("running")
                .response(response)
                .responseAll(response)
                .finished(false)
                .packageType(packageType)
                .build();
    }
}
