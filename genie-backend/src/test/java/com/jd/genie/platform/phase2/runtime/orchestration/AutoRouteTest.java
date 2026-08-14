package com.jd.genie.platform.phase2.runtime.orchestration;

import com.jd.genie.agent.agent.AgentContext;
import com.jd.genie.platform.phase2.runtime.agent.AgentTaskResult;
import com.jd.genie.platform.phase2.runtime.agent.ConfiguredAgentExecutor;
import com.jd.genie.platform.phase2.runtime.event.OrchestrationEventMapper;
import com.jd.genie.platform.phase2.runtime.plan.OrchestrationPlan;
import com.jd.genie.platform.phase2.runtime.plan.OrchestrationPlanValidator;
import com.jd.genie.platform.phase2.runtime.route.RouteDecision;
import com.jd.genie.platform.phase2contract.dto.AgentRuntimeProfile;
import com.jd.genie.platform.phase2contract.support.FakeAgentRuntimeCatalogPort;
import com.jd.genie.platform.phase2contract.support.FakeRuntimeToolCollectionPort;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.jd.genie.platform.phase2.runtime.orchestration.AgentStage6TestSupport.catalog;
import static com.jd.genie.platform.phase2.runtime.orchestration.AgentStage6TestSupport.singleStep;
import static com.jd.genie.platform.phase2.runtime.orchestration.AgentStage6TestSupport.summary;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class AutoRouteTest {

    @Test
    void autoModeDelegatesToTheRouterAndExecutesTheSelectedRoute() {
        AtomicReference<String> routedMode = new AtomicReference<>();
        AtomicReference<String> routedQuery = new AtomicReference<>();
        OrchestrationModelPort autoModel = new OrchestrationModelPort() {
            @Override
            public RouteDecision selectRoute(String query, String conversationSummary, List<com.jd.genie.platform.phase2contract.dto.AgentCapabilitySummary> candidates) {
                routedMode.set("AUTO");
                routedQuery.set(query);
                return new RouteDecision(RouteDecision.Route.ORCHESTRATED, "MULTI_AGENT_DETECTED");
            }

            @Override
            public OrchestrationPlan createPlan(String query, List<com.jd.genie.platform.phase2contract.dto.AgentCapabilitySummary> candidates,
                                                int attemptNo, java.util.Map<String, String> successes, java.util.Map<String, String> failures) {
                return new OrchestrationPlan(List.of(singleStep("step-1", "agent-a", "complete task")));
            }

            @Override
            public String summarize(String query, java.util.Map<String, String> successes, java.util.Map<String, String> failures) {
                return "final answer";
            }
        };
        FakeAgentRuntimeCatalogPort catalog = catalog("agent-a");
        ConfiguredAgentExecutor executor = mock(ConfiguredAgentExecutor.class);
        doAnswer(invocation -> AgentTaskResult.success("agent output"))
                .when(executor).execute(any(AgentContext.class), any(AgentRuntimeProfile.class), any(), anyInt());
        Phase2OrchestrationRuntime runtime = new Phase2OrchestrationRuntime(
                autoModel,
                new OrchestrationPlanValidator(),
                new SerialOrchestrationService(catalog, new FakeRuntimeToolCollectionPort(), executor, 10),
                new OrchestrationEventMapper()
        );

        assertEquals(new RouteDecision(RouteDecision.Route.ORCHESTRATED, "MULTI_AGENT_DETECTED"),
                runtime.selectRoute("AUTO", "auto query", "", List.of(summary("agent-a"))));
        assertEquals("auto query", routedQuery.get());
        assertEquals("AUTO", routedMode.get());

        AgentStage6TestSupport.RecordingChannel channel = AgentStage6TestSupport.channel();
        var observer = AgentStage6TestSupport.observer(channel);
        runtime.execute(
                AgentStage6TestSupport.USER,
                "request-1",
                "123e4567-e89b-12d3-a456-426614174000",
                "auto query",
                "summary",
                List.of(summary("agent-a")),
                new RouteDecision(RouteDecision.Route.ORCHESTRATED, "MULTI_AGENT_DETECTED"),
                observer
        );

        assertTrue(AgentStage6TestSupport.eventTypes(channel.events()).contains("ROUTE_SELECTED"));
        assertEquals(1, AgentStage6TestSupport.finishedCount(channel.events()));
        assertNull(channel.failureCode());
    }
}
