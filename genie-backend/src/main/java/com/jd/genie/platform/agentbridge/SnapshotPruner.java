package com.jd.genie.platform.agentbridge;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.contract.StreamSnapshotEnvelope;

import java.util.Iterator;
import java.util.Map;

public final class SnapshotPruner {
    public static final long DEFAULT_MAX_BYTES = 8_388_608L;
    public static final int LONG_STRING_THRESHOLD = 4_096;
    public static final String TRUNCATED_VALUE = "[TRUNCATED]";

    private final ObjectMapper objectMapper;

    public SnapshotPruner() {
        this(new ObjectMapper());
    }

    SnapshotPruner(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public StreamSnapshotEnvelope prune(StreamSnapshotEnvelope snapshot) {
        return prune(snapshot, DEFAULT_MAX_BYTES);
    }

    public StreamSnapshotEnvelope prune(StreamSnapshotEnvelope snapshot, long maxBytes) {
        validate(snapshot, maxBytes);
        ObjectNode root = toTree(snapshot);
        if (utf8Size(root) <= maxBytes) {
            return toEnvelope(root);
        }

        ArrayNode events = (ArrayNode) root.get("events");
        int finalEventIndex = lastFinalEventIndex(events);
        truncateLongStrings(events, finalEventIndex);
        root.put("truncated", true);

        removeOldestNonCriticalEvents(root, events, maxBytes);
        if (utf8Size(root) > maxBytes) {
            throw new AgentBridgeException(
                    MvpErrorCode.SNAPSHOT_TOO_LARGE,
                    "Snapshot remains larger than the configured UTF-8 byte limit after deterministic pruning"
            );
        }
        return toEnvelope(root);
    }

    public String serialize(StreamSnapshotEnvelope snapshot) {
        return serialize(snapshot, DEFAULT_MAX_BYTES);
    }

    public String serialize(StreamSnapshotEnvelope snapshot, long maxBytes) {
        StreamSnapshotEnvelope pruned = prune(snapshot, maxBytes);
        try {
            return objectMapper.writeValueAsString(pruned);
        } catch (JsonProcessingException exception) {
            throw invalidSnapshot("Snapshot cannot be serialized", exception);
        }
    }

    public long utf8Size(StreamSnapshotEnvelope snapshot) {
        validate(snapshot, Long.MAX_VALUE);
        return utf8Size(toTree(snapshot));
    }

    private void validate(StreamSnapshotEnvelope snapshot, long maxBytes) {
        if (snapshot == null || snapshot.events() == null || snapshot.payloadVersion() != 1) {
            throw invalidSnapshot("Snapshot must be a V1 envelope with a non-null events array", null);
        }
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("maxBytes must be positive");
        }
    }

    private ObjectNode toTree(StreamSnapshotEnvelope snapshot) {
        JsonNode tree = objectMapper.valueToTree(snapshot);
        if (!(tree instanceof ObjectNode root) || !(root.get("events") instanceof ArrayNode)) {
            throw invalidSnapshot("Snapshot cannot be represented as a V1 envelope", null);
        }
        return root;
    }

    private StreamSnapshotEnvelope toEnvelope(ObjectNode root) {
        try {
            return objectMapper.treeToValue(root, StreamSnapshotEnvelope.class);
        } catch (JsonProcessingException exception) {
            throw invalidSnapshot("Pruned Snapshot cannot be converted to the frozen envelope", exception);
        }
    }

    private long utf8Size(JsonNode node) {
        try {
            return objectMapper.writeValueAsBytes(node).length;
        } catch (JsonProcessingException exception) {
            throw invalidSnapshot("Snapshot UTF-8 size cannot be calculated", exception);
        }
    }

    private int lastFinalEventIndex(ArrayNode events) {
        for (int index = events.size() - 1; index >= 0; index--) {
            if (events.get(index).path("finished").asBoolean(false)) {
                return index;
            }
        }
        return -1;
    }

    private void truncateLongStrings(ArrayNode events, int finalEventIndex) {
        for (int index = 0; index < events.size(); index++) {
            if (index != finalEventIndex) {
                truncateNode(events.get(index));
            }
        }
    }

    private void truncateNode(JsonNode node) {
        if (node instanceof ObjectNode objectNode) {
            Iterator<Map.Entry<String, JsonNode>> fields = objectNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                JsonNode child = field.getValue();
                if (child.isTextual() && child.textValue().length() > LONG_STRING_THRESHOLD) {
                    objectNode.put(field.getKey(), TRUNCATED_VALUE);
                } else {
                    truncateNode(child);
                }
            }
            return;
        }
        if (node instanceof ArrayNode arrayNode) {
            for (int index = 0; index < arrayNode.size(); index++) {
                JsonNode child = arrayNode.get(index);
                if (child.isTextual() && child.textValue().length() > LONG_STRING_THRESHOLD) {
                    arrayNode.set(index, objectMapper.getNodeFactory().textNode(TRUNCATED_VALUE));
                } else {
                    truncateNode(child);
                }
            }
        }
    }

    private void removeOldestNonCriticalEvents(ObjectNode root, ArrayNode events, long maxBytes) {
        // Drop intermediate THOUGHT traces first so MAIN STATUS/OUTPUT and step OUTPUT/ERROR survive.
        int index = 0;
        while (utf8Size(root) > maxBytes && index < events.size()) {
            int finalEventIndex = lastFinalEventIndex(events);
            JsonNode event = events.get(index);
            if (index != finalEventIndex && isDroppableThoughtTrace(event)) {
                events.remove(index);
            } else {
                index++;
            }
        }
        index = 0;
        while (utf8Size(root) > maxBytes && index < events.size()) {
            int finalEventIndex = lastFinalEventIndex(events);
            JsonNode event = events.get(index);
            if (index != finalEventIndex && !isPlanOrTaskEvent(event)) {
                events.remove(index);
            } else {
                index++;
            }
        }
    }

    private static final java.util.Set<String> CRITICAL_ORCHESTRATION_EVENTS = java.util.Set.of(
            "PLAN_CREATED",
            "STEP_COMPLETED",
            "STEP_DEGRADED",
            "STEP_FAILED",
            "STEP_SKIPPED",
            "FINAL_RESPONSE",
            // Legacy V1 replan event: keep it parseable for history readers even
            // though the new V2 runtime never emits it.
            "REPLAN_STARTED"
    );

    private static final java.util.Set<String> CRITICAL_TRACE_KINDS = java.util.Set.of(
            "STATUS",
            "OUTPUT",
            "ERROR"
    );

    private boolean isDroppableThoughtTrace(JsonNode event) {
        if (!"orchestration_trace".equals(event.path("packageType").asText(""))) {
            return false;
        }
        String kind = event.path("resultMap")
                .path("orchestrationTrace")
                .path("kind")
                .asText("");
        return "THOUGHT".equals(kind);
    }

    private boolean isPlanOrTaskEvent(JsonNode event) {
        String packageType = event.path("packageType").asText("");

        // skill_execution is a transient control packet, droppable under space pressure
        if ("skill_execution".equals(packageType)) {
            return false;
        }

        String messageType = event.path("resultMap")
                .path("eventData")
                .path("messageType")
                .asText("");
        String orchestrationEventType = event.path("resultMap")
                .path("orchestrationEvent")
                .path("eventType")
                .asText("");
        if ("plan".equals(messageType)
                || "task".equals(messageType)
                || CRITICAL_ORCHESTRATION_EVENTS.contains(orchestrationEventType)) {
            return true;
        }
        if ("orchestration_trace".equals(packageType)) {
            String kind = event.path("resultMap")
                    .path("orchestrationTrace")
                    .path("kind")
                    .asText("");
            return CRITICAL_TRACE_KINDS.contains(kind);
        }
        return false;
    }

    private AgentBridgeException invalidSnapshot(String message, Throwable cause) {
        return cause == null
                ? new AgentBridgeException(MvpErrorCode.SNAPSHOT_INVALID, message)
                : new AgentBridgeException(MvpErrorCode.SNAPSHOT_INVALID, message, cause);
    }
}
