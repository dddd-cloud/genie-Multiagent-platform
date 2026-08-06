package com.jd.genie.platform.phase2.runtime.orchestration;

import com.jd.genie.platform.phase2.runtime.event.OrchestrationEventMapper;
import com.jd.genie.platform.phase2.runtime.plan.OrchestrationPlan;
import com.jd.genie.platform.phase2.runtime.plan.OrchestrationPlanValidator;
import com.jd.genie.platform.phase2.runtime.route.RouteDecision;
import com.jd.genie.platform.phase2contract.dto.AgentCapabilitySummary;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class RouterFallbackTest {
    private final List<AgentCapabilitySummary> candidates = List.of(new AgentCapabilitySummary("agent-1", 1L, "Agent", "task"));

    @Test
    void routerFailureUsesDirectFallbackWhileForcedModeBypassesRouter() {
        OrchestrationModelPort failingModel = new OrchestrationModelPort() {
            @Override
            public RouteDecision selectRoute(String query, String summary, List<AgentCapabilitySummary> available) {
                throw new IllegalStateException("router unavailable");
            }

            @Override
            public OrchestrationPlan createPlan(String query, List<AgentCapabilitySummary> available, int attempt, java.util.Map<String, String> successes, java.util.Map<String, String> failures) {
                throw new AssertionError("not used");
            }

            @Override
            public String summarize(String query, java.util.Map<String, String> successes, java.util.Map<String, String> failures) {
                throw new AssertionError("not used");
            }
        };
        Phase2OrchestrationRuntime runtime = new Phase2OrchestrationRuntime(
                failingModel, new OrchestrationPlanValidator(), mock(SerialOrchestrationService.class), new OrchestrationEventMapper()
        );

        assertEquals(new RouteDecision(RouteDecision.Route.DIRECT, "ROUTER_FALLBACK"), runtime.selectRoute("AUTO", "query", "", candidates));
        assertEquals(new RouteDecision(RouteDecision.Route.ORCHESTRATED, "FORCED_BY_REQUEST"), runtime.selectRoute("ORCHESTRATED", "query", "", candidates));
    }
}
