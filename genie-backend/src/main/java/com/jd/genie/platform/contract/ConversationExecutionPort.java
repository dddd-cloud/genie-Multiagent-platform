package com.jd.genie.platform.contract;

import java.util.List;

public interface ConversationExecutionPort {

    ConversationExecutionResult prepareExecution(
        CurrentUser currentUser,
        ConversationExecutionCommand command
    );

    void markStreaming(
        CurrentUser currentUser,
        String assistantMessageId
    );

    void complete(
        CurrentUser currentUser,
        MessageCompletionCommand command
    );

    void fail(
        CurrentUser currentUser,
        MessageFailureCommand command
    );

    void interrupt(
        CurrentUser currentUser,
        MessageFailureCommand command
    );

    List<ConversationHistoryItem> loadCompletedHistory(
        CurrentUser currentUser,
        String conversationId,
        String excludeRequestId,
        int maxTurns,
        int maxCharacters
    );
}
