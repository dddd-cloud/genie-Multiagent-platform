package com.jd.genie.platform.agentbridge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.model.response.GptProcessResult;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.contract.StreamSnapshotEnvelope;

import java.util.ArrayList;
import java.util.List;

public final class StreamSnapshotBuffer {
    public static final int PAYLOAD_VERSION = 1;

    private final ObjectMapper objectMapper;
    private final List<GptProcessResult> events = new ArrayList<>();

    public StreamSnapshotBuffer() {
        this(new ObjectMapper());
    }

    StreamSnapshotBuffer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public synchronized boolean append(GptProcessResult event) {
        if (event == null) {
            throw new IllegalArgumentException("event must not be null");
        }
        if ("heartbeat".equals(event.getPackageType())) {
            return false;
        }
        events.add(copy(event));
        return true;
    }

    public synchronized StreamSnapshotEnvelope snapshot() {
        return new StreamSnapshotEnvelope(PAYLOAD_VERSION, false, List.copyOf(events));
    }

    public synchronized List<GptProcessResult> events() {
        return List.copyOf(events);
    }

    public synchronized int size() {
        return events.size();
    }

    public synchronized void clear() {
        events.clear();
    }

    private GptProcessResult copy(GptProcessResult event) {
        try {
            return objectMapper.treeToValue(
                    objectMapper.valueToTree(event),
                    GptProcessResult.class
            );
        } catch (Exception exception) {
            throw new AgentBridgeException(
                    MvpErrorCode.SNAPSHOT_INVALID,
                    "Agent event cannot be copied into the snapshot buffer",
                    exception
            );
        }
    }
}
