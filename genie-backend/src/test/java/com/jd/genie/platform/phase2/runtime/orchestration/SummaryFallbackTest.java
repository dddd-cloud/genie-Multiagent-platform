package com.jd.genie.platform.phase2.runtime.orchestration;

import com.jd.genie.agent.agent.AgentContext;
import com.jd.genie.agent.printer.Printer;
import com.jd.genie.model.response.GptProcessResult;
import com.jd.genie.platform.agentbridge.ConversationStreamObserver;
import com.jd.genie.platform.agentbridge.StreamPersistenceObserver;
import com.jd.genie.platform.contract.CurrentUser;
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
import com.jd.genie.platform.phase2contract.support.FakeAgentRuntimeCatalogPort;
import com.jd.genie.platform.phase2contract.support.FakeRuntimeToolCollectionPort;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

class SummaryFallbackTest {
    private static final CurrentUser USER = new CurrentUser(
            "tenant-1", "user-1", "alice", "Alice", UserRole.USER
    );
    private static final List<AgentCapabilitySummary> CANDIDATES = List.of(
            new AgentCapabilitySummary("agent-a", 1L, "Agent A", "analysis")
    );

    @Test
    void summaryFailureUsesDeterministicAnswerAndKeepsSuccessfulTerminalStatus() {
        FakeAgentRuntimeCatalogPort catalog = new FakeAgentRuntimeCatalogPort();
        catalog.registerProfile(new AgentRuntimeProfile(
                "agent-a", 1L, "Agent A", "description", "prompt", "model", List.of(), List.of()
        ));
        ConfiguredAgentExecutor executor = mock(ConfiguredAgentExecutor.class);
        doReturn(AgentTaskResult.success("safe result"))
                .when(executor).execute(any(AgentContext.class), any(AgentRuntimeProfile.class), any(Printer.class), anyInt());
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

        GptProcessResult terminal = channel.events.stream().filter(GptProcessResult::isFinished).findFirst().orElseThrow();
        Map<?, ?> details = (Map<?, ?>) terminal.getResultMap().get("orchestrationEvent");
        assertEquals(1, channel.events.stream().filter(GptProcessResult::isFinished).count());
        assertEquals("result", terminal.getPackageType());
        assertEquals("SUCCESS", details.get("completionStatus"));
        assertTrue(eventTypes(channel.events).contains("SUMMARY_FALLBACK"));
        assertTrue(terminal.getResponseAll().contains("针对问题"));
        assertTrue(terminal.getResponseAll().contains("Agent A"));
        assertEquals(1, port.getCalls().stream()
                .filter(call -> call.type() == FakeConversationExecutionPort.CallType.COMPLETE)
                .count());
        assertEquals(0, port.getCalls().stream()
                .filter(call -> call.type() == FakeConversationExecutionPort.CallType.FAIL)
                .count());
    }

    private List<String> eventTypes(List<GptProcessResult> events) {
        return events.stream()
                .map(event -> event.getResultMap().get("orchestrationEvent"))
                .filter(Map.class::isInstance)
                .map(value -> (Map<?, ?>) value)
                .map(details -> details.get("eventType"))
                .filter(java.util.Objects::nonNull)
                .map(Object::toString)
                .toList();
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
                Map<String, String> successes,
                Map<String, String> failures
        ) {
            return new OrchestrationPlan(List.of(
                    new OrchestrationStep("step-1", "agent-a", "complete task", List.of())
            ));
        }

        @Override
        public String summarize(String query, Map<String, String> successes, Map<String, String> failures) {
            throw new IllegalStateException("summary unavailable");
        }
    }

    private static final class RecordingChannel implements ConversationStreamObserver.ClientChannel {
        private final List<GptProcessResult> events = new ArrayList<>();

        @Override
        public void sendEvent(GptProcessResult event) {
            events.add(event);
        }
    }
}
