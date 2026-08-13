package com.jd.genie.platform.phase2.runtime.orchestration;

import com.jd.genie.model.response.GptProcessResult;
import com.jd.genie.platform.agentbridge.ConversationStreamObserver;
import com.jd.genie.platform.agentbridge.StreamPersistenceObserver;
import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.contract.UserRole;
import com.jd.genie.platform.contract.support.FakeConversationExecutionPort;
import com.jd.genie.platform.phase2.runtime.plan.OrchestrationPlan;
import com.jd.genie.platform.phase2.runtime.plan.OrchestrationStep;
import com.jd.genie.platform.phase2.runtime.plan.OrchestrationSubTask;
import com.jd.genie.platform.phase2.runtime.route.RouteDecision;
import com.jd.genie.platform.phase2contract.dto.AgentCapabilitySummary;
import com.jd.genie.platform.phase2contract.dto.AgentRuntimeProfile;
import com.jd.genie.platform.phase2contract.enums.StepMode;
import com.jd.genie.platform.phase2contract.support.FakeAgentRuntimeCatalogPort;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Shared test data and helpers for the Stage 6 independent acceptance matrix.
 * Test-scope only; never registered as a production bean.
 */
final class AgentStage6TestSupport {

    static final CurrentUser USER = new CurrentUser(
            "tenant-1", "user-1", "alice", "Alice", UserRole.USER
    );

    private AgentStage6TestSupport() {
    }

    static FakeAgentRuntimeCatalogPort catalog(String... agentIds) {
        FakeAgentRuntimeCatalogPort catalog = new FakeAgentRuntimeCatalogPort();
        for (String agentId : agentIds) {
            catalog.registerProfile(profile(agentId));
        }
        return catalog;
    }

    static AgentRuntimeProfile profile(String agentId) {
        return new AgentRuntimeProfile(agentId, 1L, "Agent " + agentId, "description", "prompt", "model", List.of(), List.of());
    }

    static AgentCapabilitySummary summary(String agentId) {
        return new AgentCapabilitySummary(agentId, 1L, "Agent " + agentId, "analysis");
    }

    static OrchestrationStep singleStep(String stepId, String agentId, String objective) {
        return singleStep(stepId, agentId, objective, List.of());
    }

    static OrchestrationStep singleStep(String stepId, String agentId, String objective, List<String> inputRefs) {
        return new OrchestrationStep(stepId, agentId, objective, inputRefs);
    }

    static OrchestrationStep parallelStep(String stepId, List<OrchestrationSubTask> subTasks) {
        return parallelStep(stepId, subTasks, List.of());
    }

    static OrchestrationStep parallelStep(String stepId, List<OrchestrationSubTask> subTasks, List<String> inputRefs) {
        return new OrchestrationStep(stepId, StepMode.PARALLEL_AGENTS, "compare independent evidence",
                inputRefs, null, subTasks);
    }

    static OrchestrationStep mainOnlyStep(String stepId, String objective) {
        return mainOnlyStep(stepId, objective, List.of());
    }

    static OrchestrationStep mainOnlyStep(String stepId, String objective, List<String> inputRefs) {
        return new OrchestrationStep(stepId, StepMode.MAIN_ONLY, objective, inputRefs, null, List.of());
    }

    static OrchestrationSubTask subTask(String subTaskId, String agentId, String objective) {
        return new OrchestrationSubTask(subTaskId, agentId, objective);
    }

    static RecordingChannel channel() {
        return new RecordingChannel();
    }

    static ConversationStreamObserver observer(RecordingChannel channel) {
        return new ConversationStreamObserver(
                new StreamPersistenceObserver(new FakeConversationExecutionPort(), USER, "assistant-1"),
                channel
        );
    }

    static List<Map<?, ?>> orchestrationEvents(List<GptProcessResult> events) {
        List<Map<?, ?>> result = new java.util.ArrayList<>();
        for (GptProcessResult event : events) {
            Object value = event.getResultMap().get("orchestrationEvent");
            if (value instanceof Map<?, ?> map) {
                result.add(map);
            }
        }
        return List.copyOf(result);
    }

    static List<String> eventTypes(List<GptProcessResult> events) {
        return orchestrationEvents(events).stream()
                .map(event -> String.valueOf(event.get("eventType")))
                .toList();
    }

    static Map<?, ?> finalEvent(List<GptProcessResult> events) {
        GptProcessResult terminal = events.stream()
                .filter(GptProcessResult::isFinished)
                .findFirst()
                .orElseThrow();
        return (Map<?, ?>) terminal.getResultMap().get("orchestrationEvent");
    }

    static long finishedCount(List<GptProcessResult> events) {
        return events.stream().filter(GptProcessResult::isFinished).count();
    }

    static final class RecordingChannel implements ConversationStreamObserver.ClientChannel {
        private final List<GptProcessResult> events = new CopyOnWriteArrayList<>();
        private final AtomicInteger completionCount = new AtomicInteger();
        private volatile MvpErrorCode failureCode;
        private volatile String failureMessage;

        @Override
        public void sendEvent(GptProcessResult event) {
            events.add(event);
        }

        @Override
        public void sendFailure(MvpErrorCode errorCode, String message) {
            failureCode = errorCode;
            failureMessage = message;
        }

        @Override
        public void complete() {
            completionCount.incrementAndGet();
        }

        List<GptProcessResult> events() {
            return List.copyOf(events);
        }

        int completionCount() {
            return completionCount.get();
        }

        MvpErrorCode failureCode() {
            return failureCode;
        }
    }

    /**
     * Recording model with a fixed plan, a route decision and a queue of review
     * decisions. Falls back to the frozen default review policy when the queue is
     * empty (COMPLETE on success, one RETRY, then FALLBACK).
     */
    static final class RecordingModel implements OrchestrationModelPort {
        private final OrchestrationPlan plan;
        private final List<ReviewDecision> decisions;
        private final List<ReviewDecision> recordedDecisions = new CopyOnWriteArrayList<>();
        private final AtomicInteger planCount = new AtomicInteger();
        private volatile RuntimeException summarizeFailure;

        RecordingModel(OrchestrationPlan plan, ReviewDecision... decisions) {
            this.plan = plan;
            this.decisions = new java.util.ArrayList<>(List.of(decisions));
        }

        @Override
        public RouteDecision selectRoute(String query, String conversationSummary, List<AgentCapabilitySummary> candidates) {
            return new RouteDecision(RouteDecision.Route.ORCHESTRATED, "TEST");
        }

        @Override
        public OrchestrationPlan createPlan(
                String query,
                List<AgentCapabilitySummary> candidates,
                int attemptNo,
                Map<String, String> successfulResultSummaries,
                Map<String, String> failureMetadata
        ) {
            planCount.incrementAndGet();
            return plan;
        }

        @Override
        public ReviewDecision review(String objective, String safeResult, String errorCode, boolean retryable, int retryNo) {
            ReviewDecision decision = decisions.isEmpty()
                    ? defaultDecision(errorCode, retryable, retryNo)
                    : decisions.remove(0);
            recordedDecisions.add(decision);
            return decision;
        }

        @Override
        public String summarize(String query, Map<String, String> successes, Map<String, String> failures) {
            if (summarizeFailure != null) {
                throw summarizeFailure;
            }
            return "final answer";
        }

        private ReviewDecision defaultDecision(String errorCode, boolean retryable, int retryNo) {
            if (errorCode == null || errorCode.isBlank()) {
                return ReviewDecision.COMPLETE;
            }
            return retryable && retryNo == 0 ? ReviewDecision.RETRY : ReviewDecision.FALLBACK;
        }

        int planCount() {
            return planCount.get();
        }

        List<ReviewDecision> recordedDecisions() {
            return List.copyOf(recordedDecisions);
        }
    }
}
