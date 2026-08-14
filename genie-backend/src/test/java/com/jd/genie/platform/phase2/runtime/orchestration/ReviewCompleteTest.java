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

import static com.jd.genie.platform.phase2.runtime.orchestration.AgentStage6TestSupport.catalog;
import static com.jd.genie.platform.phase2.runtime.orchestration.AgentStage6TestSupport.singleStep;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class ReviewCompleteTest {

    @Test
    void reviewCompleteAcceptsTheStepWithoutAnyRetryOrFallback() {
        FakeAgentRuntimeCatalogPort catalog = catalog("agent-a");
        ConfiguredAgentExecutor executor = mock(ConfiguredAgentExecutor.class);
        AtomicInteger executions = new AtomicInteger();
        doAnswer(invocation -> {
            executions.incrementAndGet();
            return AgentTaskResult.success("complete output");
        }).when(executor).execute(any(AgentContext.class), any(AgentRuntimeProfile.class), any(), anyInt());

        AgentStage6TestSupport.RecordingModel model = new AgentStage6TestSupport.RecordingModel(
                new OrchestrationPlan(List.of(singleStep("step-1", "agent-a", "complete task"))),
                OrchestrationModelPort.ReviewDecision.COMPLETE
        );
        CopyOnWriteArrayList<String> events = new CopyOnWriteArrayList<>();
        SerialOrchestrationService service = new SerialOrchestrationService(
                catalog, new FakeRuntimeToolCollectionPort(), null, executor, 10, model
        );

        var results = service.execute(
                AgentStage6TestSupport.USER,
                "query",
                List.of(singleStep("step-1", "agent-a", "complete task")),
                (eventType, step, result, details) -> events.add(eventType)
        );

        assertEquals(1, executions.get());
        assertTrue(events.contains("STEP_STARTED"));
        assertTrue(events.contains("STEP_REVIEW_STARTED"));
        assertTrue(events.contains("STEP_COMPLETED"));
        assertTrue(events.stream().noneMatch("STEP_RETRY_STARTED"::equals));
        assertTrue(events.stream().noneMatch("STEP_FALLBACK_STARTED"::equals));
        assertTrue(events.stream().noneMatch("STEP_FAILED"::equals));
        assertEquals(AgentTaskResult.Status.SUCCESS, results.get("step-1").status());
    }
}
