package com.jd.genie.platform.agentbridge.acceptance;

import com.alibaba.fastjson.JSON;
import com.jd.genie.model.req.AgentRequest;
import com.jd.genie.model.response.AgentResponse;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class FakeAgentEventFactory {
    private static final String RESULT_MESSAGE_TYPE = "result";
    private static final int OVERSIZE_MARGIN_BYTES = 1_024;

    public List<String> successfulEvents(AgentRequest request, int totalEventCount) {
        requirePositive(totalEventCount, "totalEventCount");
        List<String> events = new ArrayList<>(totalEventCount);
        for (int sequence = 1; sequence < totalEventCount; sequence++) {
            events.add(event(request, sequence, "MVP fake agent progress " + sequence, false));
        }
        events.add(event(request, totalEventCount, "MVP fake agent completed", true));
        return List.copyOf(events);
    }

    public List<String> disconnectEvents(AgentRequest request, int eventCount) {
        requirePositive(eventCount, "eventCount");
        List<String> events = new ArrayList<>(eventCount);
        for (int sequence = 1; sequence <= eventCount; sequence++) {
            events.add(event(request, sequence, "MVP fake agent progress " + sequence, false));
        }
        return List.copyOf(events);
    }

    public List<String> noFinalEvents(AgentRequest request) {
        return List.of(
                event(request, 1, "MVP fake agent progress 1", false),
                "[DONE]"
        );
    }

    public String malformedEvent() {
        return "{malformed}";
    }

    public String snapshotTooLargeEvent(AgentRequest request, long maxSnapshotBytes) {
        if (maxSnapshotBytes <= 0) {
            throw new IllegalArgumentException("maxSnapshotBytes must be positive");
        }
        long requiredBytes;
        try {
            requiredBytes = Math.addExact(maxSnapshotBytes, OVERSIZE_MARGIN_BYTES);
        } catch (ArithmeticException error) {
            throw new IllegalArgumentException("maxSnapshotBytes is too large", error);
        }
        if (requiredBytes > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("maxSnapshotBytes exceeds the Fake Agent payload limit");
        }
        return event(
                request,
                1,
                "MVP fake oversized response " + "x".repeat((int) requiredBytes),
                true
        );
    }

    private String event(AgentRequest request, int sequence, String result, boolean finished) {
        Objects.requireNonNull(request, "request");
        Map<String, Object> resultMap = new LinkedHashMap<>();
        if (request.getAgentType() != null) {
            resultMap.put("agentType", String.valueOf(request.getAgentType()));
        }
        AgentResponse response = AgentResponse.builder()
                .requestId(request.getRequestId())
                .messageId("mvp-fake-" + sequence)
                .messageType(RESULT_MESSAGE_TYPE)
                .result(result)
                .finish(finished)
                .isFinal(finished)
                .resultMap(resultMap)
                .build();
        return JSON.toJSONString(response);
    }

    private void requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
