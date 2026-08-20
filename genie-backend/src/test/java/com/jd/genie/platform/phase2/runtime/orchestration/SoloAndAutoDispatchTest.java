package com.jd.genie.platform.phase2.runtime.orchestration;

import com.jd.genie.agent.agent.AgentContext;
import com.jd.genie.platform.phase2.runtime.agent.AgentTaskResult;
import com.jd.genie.platform.phase2.runtime.agent.ConfiguredAgentExecutor;
import com.jd.genie.platform.phase2.runtime.event.OrchestrationEventMapper;
import com.jd.genie.platform.phase2.runtime.plan.OrchestrationPlan;
import com.jd.genie.platform.phase2.runtime.plan.OrchestrationPlanValidator;
import com.jd.genie.platform.phase2.runtime.route.DispatchDecision;
import com.jd.genie.platform.phase2.runtime.route.RouteDecision;
import com.jd.genie.platform.phase2contract.dto.AgentCapabilitySummary;
import com.jd.genie.platform.phase2contract.dto.AgentRuntimeProfile;
import com.jd.genie.platform.phase2contract.dto.TeamCapabilitySummary;
import com.jd.genie.platform.phase2contract.support.FakeAgentRuntimeCatalogPort;
import com.jd.genie.platform.phase2contract.support.FakeRuntimeToolCollectionPort;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.jd.genie.platform.phase2.runtime.orchestration.AgentStage6TestSupport.catalog;
import static com.jd.genie.platform.phase2.runtime.orchestration.AgentStage6TestSupport.singleStep;
import static com.jd.genie.platform.phase2.runtime.orchestration.AgentStage6TestSupport.summary;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class SoloAndAutoDispatchTest {

    @Test
    void executeSoloRunsTheSelectedAgentWithoutSummarizing() {
        OrchestrationModelPort model = new OrchestrationModelPort() {
            @Override
            public RouteDecision selectRoute(String query, String conversationSummary, List<AgentCapabilitySummary> candidates) {
                throw new AssertionError("solo must not route");
            }

            @Override
            public OrchestrationPlan createPlan(String query, List<AgentCapabilitySummary> candidates,
                                                int attemptNo, Map<String, String> successes, Map<String, String> failures) {
                throw new AssertionError("solo must not plan");
            }

            @Override
            public String summarize(String query, Map<String, String> successes, Map<String, String> failures) {
                throw new AssertionError("solo must not summarize");
            }
        };
        FakeAgentRuntimeCatalogPort catalog = catalog("agent-a");
        ConfiguredAgentExecutor executor = mock(ConfiguredAgentExecutor.class);
        doAnswer(invocation -> AgentTaskResult.success("specialist answer"))
                .when(executor).execute(any(AgentContext.class), any(AgentRuntimeProfile.class), any(), anyInt());
        Phase2OrchestrationRuntime runtime = new Phase2OrchestrationRuntime(
                model,
                new OrchestrationPlanValidator(),
                new SerialOrchestrationService(catalog, new FakeRuntimeToolCollectionPort(), executor, 10),
                new OrchestrationEventMapper()
        );

        AgentStage6TestSupport.RecordingChannel channel = AgentStage6TestSupport.channel();
        var observer = AgentStage6TestSupport.observer(channel);
        runtime.executeSolo(
                AgentStage6TestSupport.USER,
                "request-1",
                "123e4567-e89b-12d3-a456-426614174000",
                "write the snake page",
                "",
                "",
                "",
                summary("agent-a"),
                new RouteDecision(RouteDecision.Route.ORCHESTRATED, "SOLO_AGENT"),
                observer,
                "write the snake page"
        );

        assertTrue(AgentStage6TestSupport.eventTypes(channel.events()).contains("ROUTE_SELECTED"));
        assertTrue(AgentStage6TestSupport.eventTypes(channel.events()).contains("PLAN_CREATED"));
        assertFalse(AgentStage6TestSupport.eventTypes(channel.events()).contains("SUMMARY_STARTED"));
        assertEquals(1, AgentStage6TestSupport.finishedCount(channel.events()));
        assertNull(channel.failureCode());
        assertTrue(channel.events().stream().anyMatch(event ->
                "result".equals(event.getPackageType()) && "specialist answer".equals(event.getResponse())));
    }

    @Test
    void autoDispatchPicksTheNamedTeam() {
        OrchestrationModelPort model = new OrchestrationModelPort() {
            @Override
            public RouteDecision selectRoute(String query, String conversationSummary, List<AgentCapabilitySummary> candidates) {
                return new RouteDecision(RouteDecision.Route.ORCHESTRATED, "MULTI_AGENT");
            }

            @Override
            public DispatchDecision selectDispatch(
                    String query,
                    String conversationSummary,
                    String conversationHistory,
                    List<AgentCapabilitySummary> agents,
                    List<TeamCapabilitySummary> teams
            ) {
                return DispatchDecision.team("team-1", "调研小组", "EXPLICIT_TEAM");
            }

            @Override
            public OrchestrationPlan createPlan(String query, List<AgentCapabilitySummary> candidates,
                                                int attemptNo, Map<String, String> successes, Map<String, String> failures) {
                return new OrchestrationPlan(List.of(singleStep("step-1", "agent-a", "complete task")));
            }

            @Override
            public String summarize(String query, Map<String, String> successes, Map<String, String> failures) {
                return "final";
            }
        };
        Phase2OrchestrationRuntime runtime = new Phase2OrchestrationRuntime(
                model,
                new OrchestrationPlanValidator(),
                new SerialOrchestrationService(catalog("agent-a"), new FakeRuntimeToolCollectionPort(), mock(ConfiguredAgentExecutor.class), 10),
                new OrchestrationEventMapper()
        );

        DispatchDecision decision = runtime.selectDispatch(
                "need a team",
                "",
                "",
                List.of(summary("agent-a")),
                List.of(new TeamCapabilitySummary("team-1", "调研小组", "", "主规划", List.of("竞品研究员")))
        );
        assertEquals(DispatchDecision.Kind.TEAM, decision.kind());
        assertEquals("team-1", decision.targetId());
    }
}
