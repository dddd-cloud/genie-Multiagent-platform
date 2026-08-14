package com.jd.genie.platform.phase2.runtime.orchestration;

import com.jd.genie.agent.agent.AgentContext;
import com.jd.genie.platform.phase2.runtime.agent.AgentTaskResult;
import com.jd.genie.platform.phase2.runtime.agent.ConfiguredAgentExecutor;
import com.jd.genie.platform.phase2.runtime.plan.OrchestrationStep;
import com.jd.genie.platform.phase2contract.dto.AgentRuntimeProfile;
import com.jd.genie.platform.phase2contract.support.FakeAgentRuntimeCatalogPort;
import com.jd.genie.platform.phase2contract.support.FakeRuntimeToolCollectionPort;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static com.jd.genie.platform.phase2.runtime.orchestration.AgentStage6TestSupport.catalog;
import static com.jd.genie.platform.phase2.runtime.orchestration.AgentStage6TestSupport.singleStep;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class TopLevelSerialInvariantTest {

    @Test
    void keepsExactlyOneActiveTopLevelStepAndRunsStepsInPlanOrder() {
        FakeAgentRuntimeCatalogPort catalog = catalog("agent-a", "agent-b", "agent-c");
        ConfiguredAgentExecutor executor = mock(ConfiguredAgentExecutor.class);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();
        CopyOnWriteArrayList<String> executionOrder = new CopyOnWriteArrayList<>();
        CopyOnWriteArrayList<String> startedSteps = new CopyOnWriteArrayList<>();
        CopyOnWriteArrayList<String> completedSteps = new CopyOnWriteArrayList<>();

        doAnswer(invocation -> {
            AgentContext context = invocation.getArgument(0);
            int current = active.incrementAndGet();
            maximum.accumulateAndGet(current, Math::max);
            executionOrder.add(context.getRequestId());
            try {
                Thread.sleep(30);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            }
            active.decrementAndGet();
            return AgentTaskResult.success(context.getRequestId() + " output");
        }).when(executor).execute(any(AgentContext.class), any(AgentRuntimeProfile.class), any(), anyInt());

        OrchestrationEventSink events = (eventType, step, result, details) -> {
            if ("STEP_STARTED".equals(eventType)) {
                startedSteps.add(step.stepId());
            }
            if ("STEP_COMPLETED".equals(eventType)) {
                completedSteps.add(step.stepId());
            }
        };

        SerialOrchestrationService service = new SerialOrchestrationService(
                catalog, new FakeRuntimeToolCollectionPort(), executor, 10
        );
        Map<String, AgentTaskResult> results = service.execute(
                AgentStage6TestSupport.USER,
                "query",
                List.of(
                        singleStep("step-1", "agent-a", "first"),
                        singleStep("step-2", "agent-b", "second", List.of("step-1")),
                        singleStep("step-3", "agent-c", "third", List.of("step-2"))
                ),
                events
        );

        assertEquals(1, maximum.get());
        assertEquals(List.of("step-1", "step-2", "step-3"), startedSteps);
        assertEquals(List.of("step-1", "step-2", "step-3"), completedSteps);
        assertEquals(List.of("step-1", "step-2", "step-3"), executionOrder);
        assertEquals(3, results.size());
        assertTrue(results.values().stream().allMatch(result -> result.status() == AgentTaskResult.Status.SUCCESS));
    }
}
