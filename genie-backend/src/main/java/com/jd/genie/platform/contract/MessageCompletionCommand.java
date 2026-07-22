package com.jd.genie.platform.contract;

public record MessageCompletionCommand(
    String assistantMessageId,
    String finalContent,
    String snapshotJson,
    int payloadVersion
) {
}
