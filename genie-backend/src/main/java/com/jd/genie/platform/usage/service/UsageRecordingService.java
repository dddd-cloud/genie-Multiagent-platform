package com.jd.genie.platform.usage.service;

import com.jd.genie.agent.llm.RequestTokenUsage;
import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.usage.entity.ModelUsageRecordEntity;
import com.jd.genie.platform.usage.entity.UsageTerminalState;
import com.jd.genie.platform.usage.mapper.ModelUsageMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class UsageRecordingService {
    private static final Logger log = LoggerFactory.getLogger(UsageRecordingService.class);
    private static final int MAX_REQUEST_ID_LENGTH = 64;

    private final ModelUsageMapper modelUsageMapper;
    private final ExecutionTelemetryRegistry telemetryRegistry;
    private final Clock clock;

    public UsageRecordingService(ModelUsageMapper modelUsageMapper, ExecutionTelemetryRegistry telemetryRegistry, Clock clock) {
        this.modelUsageMapper = modelUsageMapper;
        this.telemetryRegistry = telemetryRegistry;
        this.clock = clock;
    }

    /**
     * Metering is observational: a failure here must never turn a delivered answer into an error, so
     * every exception is swallowed after logging.
     */
    public void recordTerminal(CurrentUser currentUser, String assistantMessageId, UsageTerminalState terminalState) {
        if (currentUser == null || assistantMessageId == null || terminalState == null) {
            return;
        }
        ExecutionTelemetryRegistry.Telemetry telemetry = telemetryRegistry.consume(assistantMessageId);
        try {
            ModelUsageRecordEntity record = new ModelUsageRecordEntity();
            record.setId(UUID.randomUUID().toString());
            record.setTenantId(currentUser.tenantId());
            record.setUserId(currentUser.userId());
            record.setAssistantMessageId(assistantMessageId);
            record.setTerminalState(terminalState);
            record.setCreatedAt(LocalDateTime.now(clock));
            if (telemetry != null) {
                record.setConversationId(telemetry.conversationId());
                record.setRequestId(truncate(telemetry.requestId()));
                record.setDurationMs(Math.max(0L, clock.millis() - telemetry.startedAtMillis()));
                RequestTokenUsage.Snapshot usage = RequestTokenUsage.consume(telemetry.requestId());
                if (usage != null) {
                    record.setModelName(truncateModel(usage.modelName()));
                    record.setPromptTokens(usage.promptTokens());
                    record.setCompletionTokens(usage.completionTokens());
                    record.setTotalTokens(usage.totalTokens());
                }
            }
            modelUsageMapper.insertIgnore(record);
        } catch (RuntimeException ex) {
            log.warn("failed to record usage for assistantMessageId={} state={}", assistantMessageId, terminalState, ex);
        }
    }

    private static String truncate(String requestId) {
        if (requestId == null || requestId.length() <= MAX_REQUEST_ID_LENGTH) {
            return requestId;
        }
        return requestId.substring(0, MAX_REQUEST_ID_LENGTH);
    }

    private static String truncateModel(String modelName) {
        if (modelName == null || modelName.length() <= 128) {
            return modelName;
        }
        return modelName.substring(0, 128);
    }
}
