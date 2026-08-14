package com.jd.genie.platform.phase2.runtime.trace;

import com.jd.genie.model.response.GptProcessResult;
import com.jd.genie.platform.agentbridge.ConversationStreamObserver;
import com.jd.genie.platform.agentbridge.StreamPersistenceObserver;
import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.contract.UserRole;
import com.jd.genie.platform.contract.support.FakeConversationExecutionPort;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrchestrationTraceV2SubTaskTest {

    private static final CurrentUser USER = new CurrentUser(
            "tenant-1", "user-1", "alice", "Alice", UserRole.USER
    );

    @Test
    void projectsSubTaskScopeAndGivesEachSubTaskAnIndependentBudget() {
        RecordingChannel channel = new RecordingChannel();
        ConversationStreamObserver observer = new ConversationStreamObserver(
                new StreamPersistenceObserver(new FakeConversationExecutionPort(), USER, "assistant-1"),
                channel
        );
        AtomicLong sequence = new AtomicLong();
        OrchestrationTraceChannel traceChannel = new OrchestrationTraceChannel(observer, "request-1", "run-1", sequence);

        String chunk = "a".repeat(4_000);
        // sub-a exhausts its own budget (24_576 chars): 7 x 4_000 = 28_000.
        for (int index = 0; index < 7; index++) {
            traceChannel.emitSubTask(1, 0, "step-1", "sub-a", "agent-a", "Agent A",
                    OrchestrationTraceChannel.KIND_OUTPUT, chunk, false);
        }
        // sub-b must not inherit sub-a's consumed budget.
        traceChannel.emitSubTask(1, 0, "step-1", "sub-b", "agent-b", "Agent B",
                OrchestrationTraceChannel.KIND_OUTPUT, chunk, false);

        List<Map<?, ?>> traces = channel.traces();
        assertEquals(8, traces.size());
        for (Map<?, ?> trace : traces) {
            assertEquals(2, trace.get("schemaVersion"));
            assertEquals("SUBTASK", trace.get("scope"));
            assertEquals(0, trace.get("retryNo"));
            assertEquals("step-1", trace.get("stepId"));
        }
        assertEquals("sub-a", traces.get(0).get("subTaskId"));
        assertEquals("sub-b", traces.get(7).get("subTaskId"));
        assertEquals("OUTPUT", traces.get(0).get("kind"));
        // sub-a's seventh chunk hits the per-subTask budget and is truncated.
        assertEquals(4_000, String.valueOf(traces.get(0).get("text")).length());
        assertEquals(false, traces.get(0).get("truncated"));
        assertEquals(true, traces.get(6).get("truncated"));
        assertEquals(576, String.valueOf(traces.get(6).get("text")).length());
        // sub-b keeps a fresh independent budget: its first chunk survives intact.
        assertEquals(4_000, String.valueOf(traces.get(7).get("text")).length());
        assertEquals(false, traces.get(7).get("truncated"));
        assertTrue(((Number) traces.get(7).get("sequence")).longValue()
                > ((Number) traces.get(0).get("sequence")).longValue());
    }

    @Test
    void mainAndStepScopesKeepFrozenProjectionWithoutSubTaskId() {
        RecordingChannel channel = new RecordingChannel();
        ConversationStreamObserver observer = new ConversationStreamObserver(
                new StreamPersistenceObserver(new FakeConversationExecutionPort(), USER, "assistant-1"),
                channel
        );
        AtomicLong sequence = new AtomicLong();
        OrchestrationTraceChannel traceChannel = new OrchestrationTraceChannel(observer, "request-1", "run-1", sequence);

        traceChannel.emitMain(1, OrchestrationTraceChannel.KIND_STATUS, "主 Agent 开始", false);
        traceChannel.emitStep(1, "step-1", "agent-a", "Agent A",
                OrchestrationTraceChannel.KIND_OUTPUT, "step output", false);

        List<Map<?, ?>> traces = channel.traces();
        assertEquals("MAIN", traces.get(0).get("scope"));
        assertNull(traces.get(0).get("subTaskId"));
        assertNull(traces.get(0).get("stepId"));
        assertEquals("STEP", traces.get(1).get("scope"));
        assertEquals("step-1", traces.get(1).get("stepId"));
        assertNull(traces.get(1).get("subTaskId"));
        assertEquals(2, traces.get(1).get("schemaVersion"));
    }

    private static final class RecordingChannel implements ConversationStreamObserver.ClientChannel {
        private final List<GptProcessResult> events = new ArrayList<>();

        @Override
        public void sendEvent(GptProcessResult event) {
            events.add(event);
        }

        @Override
        public void sendFailure(MvpErrorCode errorCode, String message) {
        }

        List<Map<?, ?>> traces() {
            List<Map<?, ?>> result = new ArrayList<>();
            for (GptProcessResult event : events) {
                Object value = event.getResultMap().get("orchestrationTrace");
                if (value instanceof Map<?, ?> map) {
                    result.add(map);
                }
            }
            return List.copyOf(result);
        }
    }
}
