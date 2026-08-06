package com.jd.genie.platform.phase2.runtime.orchestration;

import com.jd.genie.agent.agent.AgentContext;
import com.jd.genie.agent.printer.Printer;
import com.jd.genie.model.response.GptProcessResult;
import com.jd.genie.platform.agentbridge.ConversationStreamObserver;
import com.jd.genie.platform.agentbridge.StreamPersistenceObserver;
import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.contract.UserRole;
import com.jd.genie.platform.contract.support.FakeConversationExecutionPort;
import com.jd.genie.platform.phase2.runtime.agent.AgentTaskResult;
import com.jd.genie.platform.phase2.runtime.agent.ConfiguredAgentExecutor;
import com.jd.genie.platform.phase2.runtime.event.OrchestrationEventMapper;
import com.jd.genie.platform.phase2.runtime.plan.OrchestrationPlan;
import com.jd.genie.platform.phase2.runtime.plan.OrchestrationPlanValidator;
import com.jd.genie.platform.phase2.runtime.plan.OrchestrationStep;
import com.jd.genie.platform.phase2.runtime.route.RouteDecision;
import com.jd.genie.platform.phase2contract.dto.AgentCapabilitySummary;
import com.jd.genie.platform.phase2contract.dto.AgentRuntimeProfile;
import com.jd.genie.platform.phase2contract.port.AgentRuntimeCatalogPort;
import com.jd.genie.platform.phase2contract.support.FakeRuntimeToolCollectionPort;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class Phase2OrchestrationRuntimeTest {
    private static final CurrentUser USER = new CurrentUser(
            "tenant-1", "user-1", "alice", "Alice", UserRole.USER
    );
    private static final List<AgentCapabilitySummary> CANDIDATES = List.of(
            new AgentCapabilitySummary("agent-a", 1L, "Agent A", "analysis")
    );

    @Test
    void successfulReplanDoesNotRetainPriorAttemptFailureAsPartial() {
        RecordingCatalogPort catalog = new RecordingCatalogPort();
        ConfiguredAgentExecutor executor = mock(ConfiguredAgentExecutor.class);
        AtomicInteger executions = new AtomicInteger();
        doAnswer(invocation -> executions.getAndIncrement() == 0
                ? AgentTaskResult.failure("TOOL_TIMEOUT", true)
                : AgentTaskResult.success("recovered result")
        ).when(executor).execute(any(AgentContext.class), any(AgentRuntimeProfile.class), any(Printer.class), any(Integer.TYPE));
        SerialOrchestrationService serial = new SerialOrchestrationService(
                catalog,
                new FakeRuntimeToolCollectionPort(),
                executor,
                10
        );
        RecordingModel model = new RecordingModel();
        Phase2OrchestrationRuntime runtime = new Phase2OrchestrationRuntime(
                model,
                new OrchestrationPlanValidator(),
                serial,
                new OrchestrationEventMapper()
        );
        FakeConversationExecutionPort port = new FakeConversationExecutionPort();
        RecordingChannel channel = new RecordingChannel();
        ConversationStreamObserver observer = new ConversationStreamObserver(
                new StreamPersistenceObserver(port, USER, "assistant-1"),
                channel
        );

        runtime.execute(
                USER,
                "request-1",
                "123e4567-e89b-12d3-a456-426614174000",
                "question",
                "summary",
                CANDIDATES,
                new RouteDecision(RouteDecision.Route.ORCHESTRATED, "FORCED_BY_REQUEST"),
                observer
        );

        assertEquals(Map.of("step-1", "TOOL_TIMEOUT"), model.failureMetadataByAttempt.get(2));
        assertEquals(1, channel.events.stream().filter(event -> event.isFinished()).count());
        assertEquals("SUCCESS", finalEvent(channel.events).get("completionStatus"));
        assertEquals(1, port.getCalls().stream()
                .filter(call -> call.type() == FakeConversationExecutionPort.CallType.COMPLETE)
                .count());
        assertTrue(port.getCalls().stream().noneMatch(call -> call.type() == FakeConversationExecutionPort.CallType.FAIL));
    }

    @Test
    void summaryFailureFallsBackToAHonestFinalResponse() {
        RecordingCatalogPort catalog = new RecordingCatalogPort();
        ConfiguredAgentExecutor executor = mock(ConfiguredAgentExecutor.class);
        doAnswer(invocation -> AgentTaskResult.success("safe result"))
                .when(executor).execute(any(AgentContext.class), any(AgentRuntimeProfile.class), any(Printer.class), any(Integer.TYPE));
        Phase2OrchestrationRuntime runtime = new Phase2OrchestrationRuntime(
                new SummaryFailingModel(),
                new OrchestrationPlanValidator(),
                new SerialOrchestrationService(catalog, new FakeRuntimeToolCollectionPort(), executor, 10),
                new OrchestrationEventMapper()
        );
        FakeConversationExecutionPort port = new FakeConversationExecutionPort();
        RecordingChannel channel = new RecordingChannel();
        ConversationStreamObserver observer = new ConversationStreamObserver(
                new StreamPersistenceObserver(port, USER, "assistant-1"), channel
        );

        runtime.execute(
                USER, "request-1", "123e4567-e89b-12d3-a456-426614174000", "question", "summary",
                CANDIDATES, new RouteDecision(RouteDecision.Route.ORCHESTRATED, "TEST"), observer
        );

        assertEquals(1, channel.events.stream().filter(event -> event.isFinished()).count());
        assertEquals("SUCCESS", finalEvent(channel.events).get("completionStatus"));
        assertEquals("SUMMARY_FALLBACK", eventType(channel.events, "SUMMARY_FALLBACK"));
        assertTrue(channel.events.stream().filter(GptProcessResult::isFinished).findFirst().orElseThrow()
                .getResponse().contains("## 已完成"));
        assertEquals(1, port.getCalls().stream()
                .filter(call -> call.type() == FakeConversationExecutionPort.CallType.COMPLETE).count());
    }

    @Test
    void plannerFailureProducesOneControlledFailureWithoutFinalResponse() {
        Phase2OrchestrationRuntime runtime = new Phase2OrchestrationRuntime(
                new FailingPlanModel(),
                new OrchestrationPlanValidator(),
                mock(SerialOrchestrationService.class),
                new OrchestrationEventMapper()
        );
        FakeConversationExecutionPort port = new FakeConversationExecutionPort();
        RecordingChannel channel = new RecordingChannel();
        ConversationStreamObserver observer = new ConversationStreamObserver(
                new StreamPersistenceObserver(port, USER, "assistant-1"),
                channel
        );

        runtime.execute(
                USER,
                "request-1",
                "123e4567-e89b-12d3-a456-426614174000",
                "sensitive query",
                "summary",
                CANDIDATES,
                new RouteDecision(RouteDecision.Route.ORCHESTRATED, "FORCED_BY_REQUEST"),
                observer
        );

        assertEquals(MvpErrorCode.INTERNAL_ERROR, channel.failureCode);
        assertEquals("INTERNAL_ERROR", channel.failureMessage);
        assertEquals(0, channel.events.stream().filter(GptProcessResult::isFinished).count());
        assertEquals(1, port.getCalls().stream()
                .filter(call -> call.type() == FakeConversationExecutionPort.CallType.FAIL)
                .count());
    }

    private Map<?, ?> finalEvent(List<GptProcessResult> events) {
        GptProcessResult terminal = events.stream().filter(GptProcessResult::isFinished).findFirst().orElseThrow();
        return (Map<?, ?>) terminal.getResultMap().get("orchestrationEvent");
    }

    private String eventType(List<GptProcessResult> events, String expectedType) {
        return events.stream()
                .map(event -> event.getResultMap().get("orchestrationEvent"))
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(event -> event.get("eventType"))
                .filter(expectedType::equals)
                .findFirst()
                .map(Object::toString)
                .orElseThrow();
    }

    private static final class SummaryFailingModel implements OrchestrationModelPort {
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
            return new OrchestrationPlan(List.of(new OrchestrationStep("step-1", "agent-a", "complete task", List.of())));
        }

        @Override
        public String summarize(String query, Map<String, String> successes, Map<String, String> failures) {
            throw new IllegalStateException("summary unavailable");
        }
    }

    private static final class RecordingModel implements OrchestrationModelPort {
        private final Map<Integer, Map<String, String>> failureMetadataByAttempt = new java.util.LinkedHashMap<>();

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
            failureMetadataByAttempt.put(attemptNo, Map.copyOf(failureMetadata));
            return new OrchestrationPlan(List.of(
                    new OrchestrationStep("step-" + attemptNo, "agent-a", "complete task", List.of())
            ));
        }

        @Override
        public String summarize(String query, Map<String, String> successfulResultSummaries, Map<String, String> failureMetadata) {
            return "final answer";
        }
    }

    private static final class FailingPlanModel implements OrchestrationModelPort {
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
            throw new IllegalStateException("sensitive query must not escape");
        }

        @Override
        public String summarize(String query, Map<String, String> successfulResultSummaries, Map<String, String> failureMetadata) {
            throw new AssertionError("not reached");
        }
    }

    private static final class RecordingCatalogPort implements AgentRuntimeCatalogPort {
        @Override
        public List<AgentCapabilitySummary> listOnlineCandidates(CurrentUser user, List<String> allowedAgentIds) {
            return CANDIDATES;
        }

        @Override
        public AgentRuntimeProfile loadOnlineProfile(CurrentUser user, String agentId) {
            return new AgentRuntimeProfile(agentId, 1L, "Agent A", "description", "prompt", "model", List.of(), List.of());
        }
    }

    private static final class RecordingChannel implements ConversationStreamObserver.ClientChannel {
        private final List<GptProcessResult> events = new ArrayList<>();
        private MvpErrorCode failureCode;
        private String failureMessage;

        @Override
        public void sendEvent(GptProcessResult event) {
            events.add(event);
        }

        @Override
        public void sendFailure(MvpErrorCode errorCode, String message) {
            failureCode = errorCode;
            failureMessage = message;
        }
    }
}
