package com.jd.genie.platform.usage.service;

import com.jd.genie.platform.contract.ConversationExecutionCommand;
import com.jd.genie.platform.contract.ConversationExecutionPort;
import com.jd.genie.platform.contract.ConversationExecutionResult;
import com.jd.genie.platform.contract.ConversationHistoryItem;
import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.MessageCompletionCommand;
import com.jd.genie.platform.contract.MessageFailureCommand;
import com.jd.genie.platform.usage.entity.UsageTerminalState;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;

/**
 * Adds usage metering around the conversation execution port without editing the conversation domain.
 * The delegate runs first in every method so persistence semantics stay authoritative and unchanged;
 * metering observes the outcome afterwards.
 */
@Component
@Primary
public class MeteringConversationExecutionPort implements ConversationExecutionPort {

    private final ConversationExecutionPort delegate;
    private final ExecutionTelemetryRegistry telemetryRegistry;
    private final UsageRecordingService usageRecordingService;
    private final Clock clock;

    /**
     * The delegate is qualified by bean name rather than by type: this class is itself a
     * ConversationExecutionPort, so injecting by type alone would resolve back to this @Primary bean.
     */
    public MeteringConversationExecutionPort(@Qualifier("conversationExecutionService") ConversationExecutionPort delegate,
                                            ExecutionTelemetryRegistry telemetryRegistry,
                                            UsageRecordingService usageRecordingService,
                                            Clock clock) {
        this.delegate = delegate;
        this.telemetryRegistry = telemetryRegistry;
        this.usageRecordingService = usageRecordingService;
        this.clock = clock;
    }

    @Override
    public ConversationExecutionResult prepareExecution(CurrentUser currentUser, ConversationExecutionCommand command) {
        ConversationExecutionResult result = delegate.prepareExecution(currentUser, command);
        telemetryRegistry.register(result.assistantMessageId(), result.conversationId(), result.requestId(), clock.millis());
        return result;
    }

    @Override
    public void markStreaming(CurrentUser currentUser, String assistantMessageId) {
        delegate.markStreaming(currentUser, assistantMessageId);
    }

    @Override
    public void complete(CurrentUser currentUser, MessageCompletionCommand command) {
        delegate.complete(currentUser, command);
        usageRecordingService.recordTerminal(currentUser, command == null ? null : command.assistantMessageId(),
            UsageTerminalState.COMPLETED);
    }

    @Override
    public void fail(CurrentUser currentUser, MessageFailureCommand command) {
        delegate.fail(currentUser, command);
        usageRecordingService.recordTerminal(currentUser, command == null ? null : command.assistantMessageId(),
            UsageTerminalState.FAILED);
    }

    @Override
    public void interrupt(CurrentUser currentUser, MessageFailureCommand command) {
        delegate.interrupt(currentUser, command);
        usageRecordingService.recordTerminal(currentUser, command == null ? null : command.assistantMessageId(),
            UsageTerminalState.INTERRUPTED);
    }

    @Override
    public List<ConversationHistoryItem> loadCompletedHistory(CurrentUser currentUser, String conversationId,
                                                              String excludeRequestId, int maxTurns, int maxCharacters) {
        return delegate.loadCompletedHistory(currentUser, conversationId, excludeRequestId, maxTurns, maxCharacters);
    }
}
