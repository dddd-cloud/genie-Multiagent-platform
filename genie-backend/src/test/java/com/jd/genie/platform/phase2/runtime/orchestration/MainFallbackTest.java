package com.jd.genie.platform.phase2.runtime.orchestration;

import com.jd.genie.agent.agent.AgentContext;
import com.jd.genie.platform.phase2.runtime.agent.AgentTaskResult;
import com.jd.genie.platform.phase2.runtime.agent.ConfiguredAgentExecutor;
import com.jd.genie.platform.phase2.runtime.plan.OrchestrationPlan;
import com.jd.genie.platform.phase2contract.dto.AgentRuntimeProfile;
import com.jd.genie.platform.phase2contract.support.FakeAgentRuntimeCatalogPort;
import com.jd.genie.platform.phase2contract.support.FakeRuntimeToolCollectionPort;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.jd.genie.platform.phase2.runtime.orchestration.AgentStage6TestSupport.catalog;
import static com.jd.genie.platform.phase2.runtime.orchestration.AgentStage6TestSupport.singleStep;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class MainFallbackTest {

    @Test
    void mainFallbackConvertsAnExhaustedSingleAgentStepIntoDegraded() {
        FakeAgentRuntimeCatalogPort catalog = catalog("agent-a");
        ConfiguredAgentExecutor executor = mock(ConfiguredAgentExecutor.class);
        doAnswer(invocation -> AgentTaskResult.failure("TOOL_TIMEOUT", true))
                .when(executor).execute(any(AgentContext.class), any(AgentRuntimeProfile.class), any(), anyInt());
        AtomicReference<String> fallbackObjective = new AtomicReference<>();
        AtomicInteger fallbackCalls = new AtomicInteger();
        CopyOnWriteArrayList<String> events = new CopyOnWriteArrayList<>();

        AgentStage6TestSupport.RecordingModel model = new AgentStage6TestSupport.RecordingModel(
                new OrchestrationPlan(List.of(singleStep("step-1", "agent-a", "hard task"))),
                OrchestrationModelPort.ReviewDecision.RETRY,
                OrchestrationModelPort.ReviewDecision.FALLBACK
        );
        SerialOrchestrationService service = new SerialOrchestrationService(
                catalog, new FakeRuntimeToolCollectionPort(), null, executor, 10, model,
                (objective, observer, cancellableCall) -> {
                    fallbackCalls.incrementAndGet();
                    fallbackObjective.set(objective);
                    return AgentTaskResult.success("fallback output");
                }
        );

        var results = service.execute(
                AgentStage6TestSupport.USER,
                "query",
                List.of(singleStep("step-1", "agent-a", "hard task")),
                (eventType, step, result, details) -> events.add(eventType)
        );

        assertEquals(1, fallbackCalls.get());
        assertEquals("hard task", fallbackObjective.get());
        assertTrue(events.contains("STEP_FALLBACK_STARTED"));
        assertTrue(events.contains("STEP_DEGRADED"));
        assertEquals(AgentTaskResult.Status.SUCCESS, results.get("step-1").status());
        assertEquals("fallback output", results.get("step-1").output());
    }
}
