package com.jd.genie.platform.phase2.skillruntime.execution;

import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2.skillruntime.packageinfo.SkillPackageLimits;
import com.jd.genie.platform.phase2contract.dto.BrowserSkillExecutionManifest;
import com.jd.genie.platform.phase2contract.dto.BrowserSkillExecutionResult;
import com.jd.genie.platform.phase2contract.dto.SkillEntrypointView;
import com.jd.genie.platform.phase2contract.error.Phase2ContractException;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class BrowserSkillExecutionCoordinator {
    private static final long TERMINAL_RETENTION_SECONDS = 300;
    public enum State { PENDING, COMPLETED, CANCELLED, EXPIRED }
    private final ConcurrentHashMap<String, Execution> executions = new ConcurrentHashMap<>();
    private final Clock clock;

    public BrowserSkillExecutionCoordinator() { this(Clock.systemUTC()); }
    BrowserSkillExecutionCoordinator(Clock clock) { this.clock = clock; }

    public Execution register(CurrentUser user, String skillId, SkillPackageBytesSnapshot snapshot,
                              SkillEntrypointView entrypoint, String inputJson, long timeoutMs) {
        requireUser(user);
        purgeTerminalTombstones();
        String id;
        Execution execution;
        do {
            id = UUID.randomUUID().toString();
            execution = new Execution(id, user.tenantId(), user.userId(), skillId, snapshot, entrypoint,
                inputJson, Instant.now(clock), timeoutMs);
        } while (executions.putIfAbsent(id, execution) != null);
        return execution;
    }

    public Execution lookupOwned(CurrentUser user, String executionId) {
        requireUser(user);
        Execution execution = executions.get(executionId);
        if (execution == null || !execution.ownedBy(user) || execution.state != State.PENDING)
            throw notFound();
        return execution;
    }

    public BrowserSkillExecutionResult complete(CurrentUser user, String executionId, BrowserSkillExecutionResult result) {
        requireUser(user);
        purgeTerminalTombstones();
        validateResult(executionId, result);
        Execution execution = executions.get(executionId);
        if (execution == null || !execution.ownedBy(user)) throw notFound();
        synchronized (execution) {
            if (execution.state == State.COMPLETED) {
                if (Objects.equals(execution.terminalResult, result)) return execution.terminalResult;
                throw new Phase2ContractException(MvpErrorCode.VERSION_CONFLICT, "different terminal result");
            }
            if (execution.state != State.PENDING) throw notFound();
            execution.state = State.COMPLETED;
            execution.terminalResult = result;
            execution.terminalAt = Instant.now(clock);
            execution.future.complete(result);
            return result;
        }
    }

    public void expire(String executionId) { terminal(executionId, State.EXPIRED); }
    public void cancel(String executionId) { terminal(executionId, State.CANCELLED); }
    public void release(String executionId) {
        Execution e = executions.get(executionId);
        if (e == null) return;
        synchronized (e) {
            if (e.state == State.PENDING) {
                e.state = State.CANCELLED; e.terminalAt = Instant.now(clock);
                e.future.completeExceptionally(new IllegalStateException(State.CANCELLED.name()));
            }
            e.snapshot = null;
            if (e.state != State.COMPLETED) executions.remove(executionId, e);
            else CompletableFuture.delayedExecutor(TERMINAL_RETENTION_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
                .execute(() -> executions.remove(executionId, e));
        }
    }
    public int pendingCount() { return (int) executions.values().stream().filter(e -> e.state == State.PENDING).count(); }
    public State state(String id) { Execution e = executions.get(id); return e == null ? null : e.state; }

    private void terminal(String id, State state) {
        Execution e = executions.get(id);
        if (e == null) return;
        synchronized (e) {
            if (e.state != State.PENDING) return;
            e.state = state;
            e.terminalAt = Instant.now(clock);
            e.future.completeExceptionally(new IllegalStateException(state.name()));
        }
    }

    private void purgeTerminalTombstones() {
        Instant cutoff = Instant.now(clock).minusSeconds(TERMINAL_RETENTION_SECONDS);
        executions.entrySet().removeIf(entry -> entry.getValue().state != State.PENDING
            && entry.getValue().terminalAt != null && entry.getValue().terminalAt.isBefore(cutoff));
    }

    private void validateResult(String id, BrowserSkillExecutionResult r) {
        if (r == null || r.schemaVersion() != 1 || !Objects.equals(id, r.executionId()))
            throw new Phase2ContractException(MvpErrorCode.VALIDATION_ERROR, "invalid execution result identity");
        bounded(r.outputJson(), SkillPackageLimits.MAX_OUTPUT_JSON_BYTES, "outputJson");
        bounded(r.stdout(), SkillPackageLimits.MAX_STDOUT_BYTES, "stdout");
        bounded(r.stderr(), SkillPackageLimits.MAX_STDERR_BYTES, "stderr");
        bounded(r.message(), SkillPackageLimits.MAX_MESSAGE_BYTES, "message");
    }
    private void bounded(String value, int max, String field) {
        if (value != null && value.getBytes(StandardCharsets.UTF_8).length > max)
            throw new Phase2ContractException(MvpErrorCode.VALIDATION_ERROR, field + " too large");
    }
    private void requireUser(CurrentUser user) {
        if (user == null || user.tenantId() == null || user.userId() == null)
            throw new Phase2ContractException(MvpErrorCode.AUTH_REQUIRED, "current user required");
    }
    private Phase2ContractException notFound() {
        return new Phase2ContractException(MvpErrorCode.RESOURCE_NOT_FOUND, "skill execution not found");
    }

    public static final class Execution {
        private final String executionId, tenantId, ownerId, skillId, inputJson;
        private volatile SkillPackageBytesSnapshot snapshot;
        private final SkillEntrypointView entrypoint;
        private final Instant createdAt;
        private final long timeoutMs;
        private final CompletableFuture<BrowserSkillExecutionResult> future = new CompletableFuture<>();
        private volatile State state = State.PENDING;
        private volatile BrowserSkillExecutionResult terminalResult;
        private volatile Instant terminalAt;
        private Execution(String executionId, String tenantId, String ownerId, String skillId,
                          SkillPackageBytesSnapshot snapshot, SkillEntrypointView entrypoint,
                          String inputJson, Instant createdAt, long timeoutMs) {
            this.executionId=executionId; this.tenantId=tenantId; this.ownerId=ownerId; this.skillId=skillId;
            this.snapshot=snapshot; this.entrypoint=entrypoint; this.inputJson=inputJson;
            this.createdAt=createdAt; this.timeoutMs=timeoutMs;
        }
        boolean ownedBy(CurrentUser user) { return tenantId.equals(user.tenantId()) && ownerId.equals(user.userId()); }
        public String executionId() { return executionId; }
        public String skillId() { return skillId; }
        public SkillPackageBytesSnapshot snapshot() { return snapshot; }
        public SkillEntrypointView entrypoint() { return entrypoint; }
        public String inputJson() { return inputJson; }
        public Instant createdAt() { return createdAt; }
        public long timeoutMs() { return timeoutMs; }
        public CompletableFuture<BrowserSkillExecutionResult> future() { return future; }
        public State state() { return state; }
        public BrowserSkillExecutionManifest manifest() {
            return new BrowserSkillExecutionManifest(1, executionId, entrypoint.name(), entrypoint.script(),
                entrypoint.packages(), inputJson);
        }
    }
}
