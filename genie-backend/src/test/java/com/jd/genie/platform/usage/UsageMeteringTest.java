package com.jd.genie.platform.usage;

import com.jd.genie.platform.contract.ConversationExecutionCommand;
import com.jd.genie.platform.contract.ConversationExecutionResult;
import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.MessageCompletionCommand;
import com.jd.genie.platform.contract.MessageFailureCommand;
import com.jd.genie.platform.contract.UserRole;
import com.jd.genie.platform.contract.support.FakeConversationExecutionPort;
import com.jd.genie.platform.usage.entity.ModelUsageRecordEntity;
import com.jd.genie.platform.usage.entity.UsageTerminalState;
import com.jd.genie.platform.usage.mapper.ModelUsageMapper;
import com.jd.genie.platform.usage.mapper.UsageDailyRow;
import com.jd.genie.platform.usage.mapper.UsageTotalsRow;
import com.jd.genie.platform.usage.mapper.UsageUserAggregateRow;
import com.jd.genie.platform.usage.service.ExecutionTelemetryRegistry;
import com.jd.genie.platform.usage.service.MeteringConversationExecutionPort;
import com.jd.genie.platform.usage.service.UsageRecordingService;
import com.jd.genie.agent.llm.RequestTokenUsage;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class UsageMeteringTest {

    private static final CurrentUser USER =
        new CurrentUser("tenant-1", "user-1", "alice", "Alice", UserRole.USER);

    private final RecordingUsageMapper mapper = new RecordingUsageMapper();

    @Test
    void completeRecordsOneRowWithTheDurationMeasuredFromPrepare() {
        MovingClock clock = new MovingClock(Instant.parse("2026-01-02T03:04:05Z"));
        MeteringConversationExecutionPort port = port(clock, prepared());

        ConversationExecutionResult result = port.prepareExecution(USER,
            new ConversationExecutionCommand("conv-1", "req-1", "你好", 0, "html"));
        clock.advance(1_500);
        port.complete(USER, new MessageCompletionCommand(result.assistantMessageId(), "done", "{}", 1));

        assertEquals(1, mapper.rows.size());
        ModelUsageRecordEntity row = mapper.rows.get(0);
        assertEquals(UsageTerminalState.COMPLETED, row.getTerminalState());
        assertEquals("tenant-1", row.getTenantId());
        assertEquals("user-1", row.getUserId());
        assertEquals("conv-1", row.getConversationId());
        assertEquals("req-1", row.getRequestId());
        assertEquals(1_500L, row.getDurationMs());
        assertNotNull(row.getId());
    }

    @Test
    void completeCopiesAccumulatedTokenUsageOntoTheMeteringRow() {
        RequestTokenUsage.add("req-1", "gpt-4o-mini", 11, 22, 33);
        MovingClock clock = new MovingClock(Instant.parse("2026-01-02T03:04:05Z"));
        MeteringConversationExecutionPort port = port(clock, prepared());

        ConversationExecutionResult result = port.prepareExecution(USER,
            new ConversationExecutionCommand("conv-1", "req-1", "你好", 0, "html"));
        port.complete(USER, new MessageCompletionCommand(result.assistantMessageId(), "done", "{}", 1));

        ModelUsageRecordEntity row = mapper.rows.get(0);
        assertEquals("gpt-4o-mini", row.getModelName());
        assertEquals(11L, row.getPromptTokens());
        assertEquals(22L, row.getCompletionTokens());
        assertEquals(33L, row.getTotalTokens());
        assertNull(RequestTokenUsage.consume("req-1"));
    }

    @Test
    void failAndInterruptAreRecordedWithTheirOwnTerminalState() {
        MeteringConversationExecutionPort port =
            port(new MovingClock(Instant.EPOCH), new FakeConversationExecutionPort());

        port.fail(USER, new MessageFailureCommand("assistant-fail", "AGENT_DOWNSTREAM_ERROR", "boom", null, 1));
        port.interrupt(USER, new MessageFailureCommand("assistant-stop", "CLIENT_DISCONNECTED", "left", null, 1));

        assertEquals(List.of(UsageTerminalState.FAILED, UsageTerminalState.INTERRUPTED),
            mapper.rows.stream().map(ModelUsageRecordEntity::getTerminalState).toList());
    }

    @Test
    void aReplayedTerminalEventDoesNotDoubleCount() {
        MeteringConversationExecutionPort port =
            port(new MovingClock(Instant.EPOCH), new FakeConversationExecutionPort());
        MessageCompletionCommand command = new MessageCompletionCommand("assistant-1", "done", "{}", 1);

        port.complete(USER, command);
        port.complete(USER, command);

        assertEquals(2, mapper.rows.size(), "both inserts are attempted");
        assertEquals(1, mapper.persistedKeys.size(), "the unique key keeps only the first row");
    }

    @Test
    void terminalWithoutKnownTelemetryStillRecordsTheCallWithoutADuration() {
        MeteringConversationExecutionPort port =
            port(new MovingClock(Instant.EPOCH), new FakeConversationExecutionPort());

        port.complete(USER, new MessageCompletionCommand("assistant-unknown", "done", "{}", 1));

        assertEquals(1, mapper.rows.size());
        assertNull(mapper.rows.get(0).getDurationMs());
        assertNull(mapper.rows.get(0).getConversationId());
    }

    @Test
    void meteringFailureDoesNotBreakTheConversation() {
        ExplodingUsageMapper exploding = new ExplodingUsageMapper();
        MovingClock clock = new MovingClock(Instant.EPOCH);
        ExecutionTelemetryRegistry registry = new ExecutionTelemetryRegistry(10);
        FakeConversationExecutionPort delegate = new FakeConversationExecutionPort();
        MeteringConversationExecutionPort port = new MeteringConversationExecutionPort(
            delegate, registry, new UsageRecordingService(exploding, registry, clock), clock);

        port.complete(USER, new MessageCompletionCommand("assistant-1", "done", "{}", 1));

        assertEquals(1, delegate.getCalls().stream()
            .filter(call -> call.type() == FakeConversationExecutionPort.CallType.COMPLETE).count());
    }

    @Test
    void theDecoratorImplementsTheUnchangedPortContract() {
        assertSame(com.jd.genie.platform.contract.ConversationExecutionPort.class,
            MeteringConversationExecutionPort.class.getInterfaces()[0]);
    }

    @Test
    void everyCallIsForwardedToTheDelegateUnchanged() {
        FakeConversationExecutionPort delegate = prepared();
        MeteringConversationExecutionPort port = port(new MovingClock(Instant.EPOCH), delegate);

        port.prepareExecution(USER, new ConversationExecutionCommand("conv-1", "req-1", "你好", 0, "html"));
        port.markStreaming(USER, "assistant-1");
        port.loadCompletedHistory(USER, "conv-1", "req-1", 6, 1000);

        assertEquals(List.of(
            FakeConversationExecutionPort.CallType.PREPARE_EXECUTION,
            FakeConversationExecutionPort.CallType.MARK_STREAMING,
            FakeConversationExecutionPort.CallType.LOAD_COMPLETED_HISTORY
        ), delegate.getCalls().stream().map(FakeConversationExecutionPort.CallRecord::type).toList());
    }

    @Test
    void theTelemetryRegistryIsBoundedAndEvictsInInsertionOrder() {
        ExecutionTelemetryRegistry registry = new ExecutionTelemetryRegistry(2);
        registry.register("a", "conv-a", "req-a", 1L);
        registry.register("b", "conv-b", "req-b", 2L);
        registry.register("c", "conv-c", "req-c", 3L);

        assertNull(registry.consume("a"), "the oldest entry is evicted once the cap is exceeded");
        assertNotNull(registry.consume("b"));
        assertNotNull(registry.consume("c"));
    }

    @Test
    void consumingTelemetryTwiceYieldsNothingTheSecondTime() {
        ExecutionTelemetryRegistry registry = new ExecutionTelemetryRegistry(10);
        registry.register("a", "conv-a", "req-a", 7L);

        ExecutionTelemetryRegistry.Telemetry first = registry.consume("a");
        assertNotNull(first);
        assertEquals(7L, first.startedAtMillis());
        assertNull(registry.consume("a"));
    }

    private MeteringConversationExecutionPort port(Clock clock, FakeConversationExecutionPort delegate) {
        ExecutionTelemetryRegistry registry = new ExecutionTelemetryRegistry(100);
        return new MeteringConversationExecutionPort(
            delegate, registry, new UsageRecordingService(mapper, registry, clock), clock);
    }

    private static FakeConversationExecutionPort prepared() {
        FakeConversationExecutionPort delegate = new FakeConversationExecutionPort();
        delegate.setPrepareExecutionResult(
            new ConversationExecutionResult("conv-1", "req-1", "user-msg-1", "assistant-1", 1L));
        return delegate;
    }

    private static final class MovingClock extends Clock {
        private Instant now;

        private MovingClock(Instant start) {
            this.now = start;
        }

        void advance(long millis) {
            now = now.plusMillis(millis);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    /** Mimics INSERT IGNORE against the (tenant_id, assistant_message_id) unique key. */
    private static class RecordingUsageMapper implements ModelUsageMapper {
        private final List<ModelUsageRecordEntity> rows = new ArrayList<>();
        private final Set<String> persistedKeys = new HashSet<>();

        @Override
        public int insertIgnore(ModelUsageRecordEntity record) {
            rows.add(record);
            return persistedKeys.add(record.getTenantId() + "|" + record.getAssistantMessageId()) ? 1 : 0;
        }

        @Override
        public UsageTotalsRow sumTenantTotals(String tenantId, LocalDateTime from, LocalDateTime to) {
            return new UsageTotalsRow();
        }

        @Override
        public UsageTotalsRow sumUserTotals(String tenantId, String userId, LocalDateTime from, LocalDateTime to) {
            return new UsageTotalsRow();
        }

        @Override
        public List<UsageDailyRow> listTenantDaily(String tenantId, LocalDateTime from, LocalDateTime to) {
            return List.of();
        }

        @Override
        public List<UsageDailyRow> listUserDaily(String tenantId, String userId, LocalDateTime from, LocalDateTime to) {
            return List.of();
        }

        @Override
        public List<UsageUserAggregateRow> listUserAggregates(String tenantId, LocalDateTime from, LocalDateTime to,
                                                             int offset, int limit) {
            return List.of();
        }
    }

    private static final class ExplodingUsageMapper extends RecordingUsageMapper {
        @Override
        public int insertIgnore(ModelUsageRecordEntity record) {
            throw new IllegalStateException("usage table unavailable");
        }
    }
}
