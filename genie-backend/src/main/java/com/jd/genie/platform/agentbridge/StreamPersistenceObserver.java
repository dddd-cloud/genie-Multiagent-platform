package com.jd.genie.platform.agentbridge;

import com.jd.genie.platform.contract.ConversationExecutionPort;
import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.MessageCompletionCommand;
import com.jd.genie.platform.contract.MessageFailureCommand;
import com.jd.genie.platform.contract.MvpErrorCode;

import java.util.Objects;

public final class StreamPersistenceObserver {
    private final ConversationExecutionPort executionPort;
    private final CurrentUser currentUser;
    private final String assistantMessageId;

    public StreamPersistenceObserver(
            ConversationExecutionPort executionPort,
            CurrentUser currentUser,
            String assistantMessageId
    ) {
        this.executionPort = Objects.requireNonNull(executionPort, "executionPort");
        this.currentUser = Objects.requireNonNull(currentUser, "currentUser");
        this.assistantMessageId = requireText(assistantMessageId, "assistantMessageId");
    }

    public void markStreaming() {
        executionPort.markStreaming(currentUser, assistantMessageId);
    }

    public void complete(String finalContent, String snapshotJson, int payloadVersion) {
        executionPort.complete(
                currentUser,
                new MessageCompletionCommand(
                        assistantMessageId,
                        requireText(finalContent, "finalContent"),
                        requireText(snapshotJson, "snapshotJson"),
                        payloadVersion
                )
        );
    }

    public void fail(
            MvpErrorCode errorCode,
            String errorMessage,
            String partialSnapshotJson,
            Integer payloadVersion
    ) {
        executionPort.fail(
                currentUser,
                failureCommand(errorCode, errorMessage, partialSnapshotJson, payloadVersion)
        );
    }

    public void interrupt(
            MvpErrorCode errorCode,
            String errorMessage,
            String partialSnapshotJson,
            Integer payloadVersion
    ) {
        executionPort.interrupt(
                currentUser,
                failureCommand(errorCode, errorMessage, partialSnapshotJson, payloadVersion)
        );
    }

    public String assistantMessageId() {
        return assistantMessageId;
    }

    private MessageFailureCommand failureCommand(
            MvpErrorCode errorCode,
            String errorMessage,
            String partialSnapshotJson,
            Integer payloadVersion
    ) {
        Objects.requireNonNull(errorCode, "errorCode");
        return new MessageFailureCommand(
                assistantMessageId,
                errorCode.name(),
                requireText(errorMessage, "errorMessage"),
                partialSnapshotJson,
                payloadVersion
        );
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
