package com.jd.genie.platform.phase2.runtime.orchestration;

import com.jd.genie.agent.agent.AgentContext;
import com.jd.genie.platform.phase2.runtime.agent.AgentTaskResult;
import com.jd.genie.platform.phase2.runtime.agent.ConfiguredAgentExecutor;
import com.jd.genie.platform.phase2contract.dto.AgentRuntimeProfile;
import com.jd.genie.platform.phase2contract.support.FakeAgentRuntimeCatalogPort;
import com.jd.genie.platform.phase2contract.support.FakeRuntimeToolCollectionPort;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static com.jd.genie.platform.phase2.runtime.orchestration.AgentStage6TestSupport.catalog;
import static com.jd.genie.platform.phase2.runtime.orchestration.AgentStage6TestSupport.mainOnlyStep;
import static com.jd.genie.platform.phase2.runtime.orchestration.AgentStage6TestSupport.singleStep;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class MainOnlyNoFallbackTest {

    @Test
    void mainOnlySuccessCompletesWithoutReviewRetryOrFallback() {
        FakeAgentRuntimeCatalogPort catalog = catalog();
        ConfiguredAgentExecutor executor = mock(ConfiguredAgentExecutor.class);
        AtomicInteger mainCalls = new AtomicInteger();
        CopyOnWriteArrayList<String> events = new CopyOnWriteArrayList<>();
        SerialOrchestrationService service = new SerialOrchestrationService(
                catalog, new FakeRuntimeToolCollectionPort(), null, executor, 10, null,
                (objective, observer, cancellableCall) -> {
                    mainCalls.incrementAndGet();
                    return AgentTaskResult.success("main handled it");
                }
        );

        var results = service.execute(
                AgentStage6TestSupport.USER,
                "query",
                List.of(mainOnlyStep("step-1", "prepare scope")),
                (eventType, step, result, details) -> events.add(eventType)
        );

        assertEquals(1, mainCalls.get());
        assertTrue(events.contains("STEP_STARTED"));
        assertTrue(events.contains("STEP_COMPLETED"));
        assertTrue(events.stream().noneMatch("STEP_REVIEW_STARTED"::equals));
        assertTrue(events.stream().noneMatch("STEP_FALLBACK_STARTED"::equals));
        assertTrue(events.stream().noneMatch("STEP_DEGRADED"::equals));
        assertEquals(AgentTaskResult.Status.SUCCESS, results.get("step-1").status());
    }

    @Test
    void mainOnlyFailureSkipsLaterStepsAndNeverFallsBackToMain() {
        FakeAgentRuntimeCatalogPort catalog = catalog();
        ConfiguredAgentExecutor executor = mock(ConfiguredAgentExecutor.class);
        doAnswer(invocation -> AgentTaskResult.success("never reached"))
                .when(executor).execute(any(AgentContext.class), any(AgentRuntimeProfile.class), any(), anyInt());
        AtomicInteger mainCalls = new AtomicInteger();
        CopyOnWriteArrayList<String> events = new CopyOnWriteArrayList<>();
        SerialOrchestrationService service = new SerialOrchestrationService(
                catalog, new FakeRuntimeToolCollectionPort(), null, executor, 10, null,
                (objective, observer, cancellableCall) -> {
                    mainCalls.incrementAndGet();
                    return AgentTaskResult.failure("EXECUTION_ERROR", false);
                }
        );

        var results = service.execute(
                AgentStage6TestSupport.USER,
                "query",
                List.of(
                        mainOnlyStep("step-1", "hard objective"),
                        singleStep("step-2", "agent-a", "must be skipped", List.of("step-1"))
                ),
                (eventType, step, result, details) -> events.add(eventType)
        );

        assertEquals(1, mainCalls.get());
        assertEquals(AgentTaskResult.Status.FAILURE, results.get("step-1").status());
        assertEquals(AgentTaskResult.Status.FAILURE, results.get("step-2").status());
        assertTrue(events.contains("STEP_FAILED"));
        assertTrue(events.contains("STEP_SKIPPED"));
        assertTrue(events.stream().noneMatch("STEP_FALLBACK_STARTED"::equals));
        assertTrue(events.stream().noneMatch("STEP_DEGRADED"::equals));
    }

    @Test
    void mainOnlyRetriesAtMostOnceThenFailsDirectly() {
        FakeAgentRuntimeCatalogPort catalog = catalog();
        ConfiguredAgentExecutor executor = mock(ConfiguredAgentExecutor.class);
        AtomicInteger mainCalls = new AtomicInteger();
        CopyOnWriteArrayList<String> events = new CopyOnWriteArrayList<>();
        SerialOrchestrationService service = new SerialOrchestrationService(
                catalog, new FakeRuntimeToolCollectionPort(), null, executor, 10, null,
                (objective, observer, cancellableCall) -> {
                    int call = mainCalls.incrementAndGet();
                    return call == 1
                            ? AgentTaskResult.failure("TOOL_TIMEOUT", true)
                            : AgentTaskResult.success("recovered by main");
                }
        );

        var results = service.execute(
                AgentStage6TestSupport.USER,
                "query",
                List.of(mainOnlyStep("step-1", "flaky objective")),
                (eventType, step, result, details) -> events.add(eventType)
        );

        assertEquals(2, mainCalls.get());
        assertEquals(1, events.stream().filter("STEP_RETRY_STARTED"::equals).count());
        assertTrue(events.contains("STEP_COMPLETED"));
        assertTrue(events.stream().noneMatch("STEP_DEGRADED"::equals));
        assertEquals(AgentTaskResult.Status.SUCCESS, results.get("step-1").status());
    }
}
