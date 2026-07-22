package com.jd.genie.platform.conversation.snapshot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.conversation.exception.ConversationException;

import java.nio.charset.StandardCharsets;

public class SnapshotValidator {
    public static final int PAYLOAD_VERSION = 1;

    private final ObjectMapper objectMapper;
    private final int maxBytes;

    public SnapshotValidator(ObjectMapper objectMapper, int maxBytes) {
        this.objectMapper = objectMapper;
        this.maxBytes = maxBytes;
    }

    public void validate(String snapshotJson, int commandPayloadVersion) {
        if (snapshotJson == null || snapshotJson.isBlank()) {
            throw snapshotInvalid();
        }
        if (commandPayloadVersion != PAYLOAD_VERSION) {
            throw snapshotInvalid();
        }
        if (snapshotJson.getBytes(StandardCharsets.UTF_8).length > maxBytes) {
            throw new ConversationException(MvpErrorCode.SNAPSHOT_TOO_LARGE, "Snapshot exceeds byte limit");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(snapshotJson);
        } catch (Exception exception) {
            throw new ConversationException(MvpErrorCode.SNAPSHOT_INVALID, "Snapshot is invalid", exception);
        }
        if (root == null || !root.isObject()) {
            throw snapshotInvalid();
        }
        JsonNode payloadVersion = root.get("payloadVersion");
        if (payloadVersion == null || !payloadVersion.isInt() || payloadVersion.asInt() != PAYLOAD_VERSION) {
            throw snapshotInvalid();
        }
        if (payloadVersion.asInt() != commandPayloadVersion) {
            throw snapshotInvalid();
        }
    }

    public String validOrNull(String snapshotJson, Integer commandPayloadVersion) {
        if (snapshotJson == null || snapshotJson.isBlank()) {
            return null;
        }
        try {
            validate(snapshotJson, commandPayloadVersion == null ? PAYLOAD_VERSION : commandPayloadVersion);
            return snapshotJson;
        } catch (ConversationException exception) {
            return null;
        }
    }

    private ConversationException snapshotInvalid() {
        return new ConversationException(MvpErrorCode.SNAPSHOT_INVALID, "Snapshot is invalid");
    }
}
