package com.jd.genie.platform.phase2.runtime.orchestration;

import com.jd.genie.agent.agent.AgentContext;
import com.jd.genie.platform.phase2.runtime.agent.AgentTaskResult;
import com.jd.genie.platform.phase2.runtime.agent.ConfiguredAgentExecutor;
import com.jd.genie.platform.phase2.runtime.plan.OrchestrationPlan;
import com.jd.genie.platform.phase2.runtime.plan.OrchestrationStep;
import com.jd.genie.platform.phase2contract.dto.AgentRuntimeProfile;
import com.jd.genie.platform.phase2contract.support.FakeAgentRuntimeCatalogPort;
import com.jd.genie.platform.phase2contract.support.FakeRuntimeToolCollectionPort;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static com.jd.genie.platform.phase2.runtime.orchestration.AgentStage6TestSupport.catalog;
import static com.jd.genie.platform.phase2.runtime.orchestration.AgentStage6TestSupport.parallelStep;
import static com.jd.genie.platform.phase2.runtime.orchestration.AgentStage6TestSupport.subTask;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class SelectiveRetryTest {

    @Test
    void retriesOnlyTheFailedRetryableSubTaskAndNeverReRunsTheValidOne() {
        FakeAgentRuntimeCatalogPort catalog = catalog("agent-a", "agent-b");
        ConfiguredAgentExecutor executor = mock(ConfiguredAgentExecutor.class);
        Map<String, AtomicInteger> invocations = new ConcurrentHashMap<>();

        doAnswer(invocation -> {
            AgentContext context = invocation.getArgument(0);
            int call = invocations.computeIfAbsent(context.getRequestId(), key -> new AtomicInteger()).incrementAndGet();
            if ("sub-ok".equals(context.getRequestId())) {
                return AgentTaskResult.success("valid output");
            }
            return call == 1
                    ? AgentTaskResult.failure("TOOL_TIMEOUT", true)
                    : AgentTaskResult.success("recovered output");
        }).when(executor).execute(any(AgentContext.class), any(AgentRuntimeProfile.class), any(), anyInt());

        AgentStage6TestSupport.RecordingModel model = new AgentStage6TestSupport.RecordingModel(
                new OrchestrationPlan(List.of(parallelStep("parallel", List.of(
                        subTask("sub-ok", "agent-a", "stable angle"),
                        subTask("sub-bad", "agent-b", "flaky angle")
                )))),
                OrchestrationModelPort.ReviewDecision.RETRY,
                OrchestrationModelPort.ReviewDecision.COMPLETE
        );
        CopyOnWriteArrayList<String> events = new CopyOnWriteArrayList<>();
        SerialOrchestrationService service = new SerialOrchestrationService(
                catalog, new FakeRuntimeToolCollectionPort(), null, executor, 10, model
        );

        Map<String, AgentTaskResult> results = service.execute(
                AgentStage6TestSupport.USER,
                "query",
                List.of(parallelStep("parallel", List.of(
                        subTask("sub-ok", "agent-a", "stable angle"),
                        subTask("sub-bad", "agent-b", "flaky angle")
                ))),
                (eventType, step, result, details) -> events.add(eventType)
        );

        assertEquals(1, invocations.get("sub-ok").get());
        assertEquals(2, invocations.get("sub-bad").get());
        assertTrue(events.contains("STEP_RETRY_STARTED"));
        assertTrue(events.contains("STEP_COMPLETED"));
        assertEquals(AgentTaskResult.Status.SUCCESS, results.get("parallel").status());
        assertTrue(results.get("parallel").output().contains("valid output"));
        assertTrue(results.get("parallel").output().contains("recovered output"));
    }
}
