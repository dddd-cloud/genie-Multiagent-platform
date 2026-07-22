package com.jd.genie.platform.contract.support;

import com.jd.genie.platform.contract.ConversationExecutionCommand;
import com.jd.genie.platform.contract.ConversationExecutionPort;
import com.jd.genie.platform.contract.ConversationExecutionResult;
import com.jd.genie.platform.contract.ConversationHistoryItem;
import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.MessageCompletionCommand;
import com.jd.genie.platform.contract.MessageFailureCommand;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class FakeConversationExecutionPort implements ConversationExecutionPort {

    public enum CallType {
        PREPARE_EXECUTION,
        MARK_STREAMING,
        COMPLETE,
        FAIL,
        INTERRUPT,
        LOAD_COMPLETED_HISTORY
    }

    public record CallRecord(
        CallType type,
        CurrentUser currentUser,
        ConversationExecutionCommand command,
        String assistantMessageId,
        MessageCompletionCommand completionCommand,
        MessageFailureCommand failureCommand,
        String conversationId,
        String excludeRequestId,
        int maxTurns,
        int maxCharacters
    ) {
    }

    private final List<CallRecord> calls = new CopyOnWriteArrayList<>();

    private ConversationExecutionResult prepareExecutionResult;
    private List<ConversationHistoryItem> loadCompletedHistoryResult = List.of();

    public void setPrepareExecutionResult(ConversationExecutionResult result) {
        this.prepareExecutionResult = result;
    }

    public void setLoadCompletedHistoryResult(List<ConversationHistoryItem> result) {
        this.loadCompletedHistoryResult = result;
    }

    public List<CallRecord> getCalls() {
        return Collections.unmodifiableList(new ArrayList<>(calls));
    }

    public void reset() {
        calls.clear();
        prepareExecutionResult = null;
        loadCompletedHistoryResult = List.of();
    }

    @Override
    public ConversationExecutionResult prepareExecution(
        CurrentUser currentUser,
        ConversationExecutionCommand command
    ) {
        calls.add(new CallRecord(
            CallType.PREPARE_EXECUTION,
            currentUser,
            command,
            null,
            null,
            null,
            null,
            null,
            0,
            0
        ));
        if (prepareExecutionResult == null) {
            throw new IllegalStateException("prepareExecutionResult is not configured");
        }
        return prepareExecutionResult;
    }

    @Override
    public void markStreaming(CurrentUser currentUser, String assistantMessageId) {
        calls.add(new CallRecord(
            CallType.MARK_STREAMING,
            currentUser,
            null,
            assistantMessageId,
            null,
            null,
            null,
            null,
            0,
            0
        ));
    }

    @Override
    public void complete(CurrentUser currentUser, MessageCompletionCommand command) {
        calls.add(new CallRecord(
            CallType.COMPLETE,
            currentUser,
            null,
            command.assistantMessageId(),
            command,
            null,
            null,
            null,
            0,
            0
        ));
    }

    @Override
    public void fail(CurrentUser currentUser, MessageFailureCommand command) {
        calls.add(new CallRecord(
            CallType.FAIL,
            currentUser,
            null,
            command.assistantMessageId(),
            null,
            command,
            null,
            null,
            0,
            0
        ));
    }

    @Override
    public void interrupt(CurrentUser currentUser, MessageFailureCommand command) {
        calls.add(new CallRecord(
            CallType.INTERRUPT,
            currentUser,
            null,
            command.assistantMessageId(),
            null,
            command,
            null,
            null,
            0,
            0
        ));
    }

    @Override
    public List<ConversationHistoryItem> loadCompletedHistory(
        CurrentUser currentUser,
        String conversationId,
        String excludeRequestId,
        int maxTurns,
        int maxCharacters
    ) {
        calls.add(new CallRecord(
            CallType.LOAD_COMPLETED_HISTORY,
            currentUser,
            null,
            null,
            null,
            null,
            conversationId,
            excludeRequestId,
            maxTurns,
            maxCharacters
        ));
        return loadCompletedHistoryResult;
    }
}
