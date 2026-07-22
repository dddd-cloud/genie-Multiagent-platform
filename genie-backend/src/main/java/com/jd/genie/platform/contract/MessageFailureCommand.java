package com.jd.genie.platform.contract;

public record MessageFailureCommand(
    String assistantMessageId,
    String errorCode,
    String errorMessage,
    String partialSnapshotJson,
    Integer payloadVersion
) {
}
