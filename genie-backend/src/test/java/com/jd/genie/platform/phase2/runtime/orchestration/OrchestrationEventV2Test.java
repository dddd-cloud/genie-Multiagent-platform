package com.jd.genie.platform.phase2.runtime.orchestration;

import com.jd.genie.agent.agent.AgentContext;
import com.jd.genie.platform.phase2.runtime.agent.AgentTaskResult;
import com.jd.genie.platform.phase2.runtime.agent.ConfiguredAgentExecutor;
import com.jd.genie.platform.phase2.runtime.event.OrchestrationEventMapper;
import com.jd.genie.platform.phase2.runtime.plan.OrchestrationPlan;
import com.jd.genie.platform.phase2.runtime.plan.OrchestrationPlanValidator;
import com.jd.genie.platform.phase2.runtime.plan.OrchestrationSubTask;
import com.jd.genie.platform.phase2.runtime.route.RouteDecision;
import com.jd.genie.platform.phase2contract.dto.AgentRuntimeProfile;
import com.jd.genie.platform.phase2contract.support.FakeAgentRuntimeCatalogPort;
import com.jd.genie.platform.phase2contract.support.FakeRuntimeToolCollectionPort;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.jd.genie.platform.phase2.runtime.orchestration.AgentStage6TestSupport.catalog;
import static com.jd.genie.platform.phase2.runtime.orchestration.AgentStage6TestSupport.parallelStep;
import static com.jd.genie.platform.phase2.runtime.orchestration.AgentStage6TestSupport.subTask;
import static com.jd.genie.platform.phase2.runtime.orchestration.AgentStage6TestSupport.summary;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class OrchestrationEventV2Test {

    @Test
    void projectsFrozenV2EventOrderFieldsAndSingleTerminalForAParallelRun() {
        FakeAgentRuntimeCatalogPort catalog = catalog("agent-a", "agent-b");
        ConfiguredAgentExecutor executor = mock(ConfiguredAgentExecutor.class);
        doAnswer(invocation -> AgentTaskResult.success(
                invocation.getArgument(0, AgentContext.class).getRequestId() + " output"
        )).when(executor).execute(any(AgentContext.class), any(AgentRuntimeProfile.class), any(), anyInt());

        AgentStage6TestSupport.RecordingModel model = new AgentStage6TestSupport.RecordingModel(
                new OrchestrationPlan(List.of(parallelStep("parallel", List.of(
                        new OrchestrationSubTask("sub-a", "agent-a", "angle A"),
                        new OrchestrationSubTask("sub-b", "agent-b", "angle B")
                ))))
        );
        Phase2OrchestrationRuntime runtime = new Phase2OrchestrationRuntime(
                model,
                new OrchestrationPlanValidator(),
                new SerialOrchestrationService(catalog, new FakeRuntimeToolCollectionPort(), null, executor, 10, model),
                new OrchestrationEventMapper()
        );
        AgentStage6TestSupport.RecordingChannel channel = AgentStage6TestSupport.channel();
        var observer = AgentStage6TestSupport.observer(channel);

        runtime.execute(
                AgentStage6TestSupport.USER,
                "request-1",
                "123e4567-e89b-12d3-a456-426614174000",
                "question",
                "summary",
                List.of(summary("agent-a"), summary("agent-b")),
                new RouteDecision(RouteDecision.Route.ORCHESTRATED, "FORCED_BY_REQUEST"),
                observer
        );

        List<Map<?, ?>> events = AgentStage6TestSupport.orchestrationEvents(channel.events());
        assertFalse(events.isEmpty());

        // V2 shape: schemaVersion=2, eventId = requestId:sequence.
        // Sequences are assigned atomically and strictly increasing; concurrent
        // SUBTASK emitters may interleave their arrival order, so uniqueness is
        // asserted globally while monotonicity is asserted on the main-thread chain.
        java.util.Set<Long> allSequences = new java.util.LinkedHashSet<>();
        for (Map<?, ?> event : events) {
            assertEquals(2, event.get("schemaVersion"));
            long sequence = ((Number) event.get("sequence")).longValue();
            allSequences.add(sequence);
            assertEquals("request-1:" + sequence, event.get("eventId"));
        }
        assertEquals(events.size(), allSequences.size());
        assertEquals(1L, allSequences.iterator().next());

        List<String> types = events.stream().map(event -> String.valueOf(event.get("eventType"))).toList();
        assertFalse(types.contains("REPLAN_STARTED"));

        int planIdx = types.indexOf("PLAN_CREATED");
        int stepStart = types.indexOf("STEP_STARTED");
        int parallelStart = types.indexOf("PARALLEL_STARTED");
        int reviewStart = types.indexOf("STEP_REVIEW_STARTED");
        int stepCompleted = types.indexOf("STEP_COMPLETED");
        int summaryStart = types.indexOf("SUMMARY_STARTED");
        int summaryCompleted = types.indexOf("SUMMARY_COMPLETED");
        int finalIdx = types.indexOf("FINAL_RESPONSE");
        assertTrue(planIdx >= 0 && planIdx < stepStart);
        assertTrue(stepStart < parallelStart);
        assertTrue(parallelStart < reviewStart);
        assertTrue(reviewStart < stepCompleted);
        assertTrue(stepCompleted < summaryStart);
        assertTrue(summaryStart <= summaryCompleted);
        assertTrue(summaryCompleted < finalIdx);

        // Main-thread chain keeps strictly increasing sequence values.
        assertTrue(seqOf(events, planIdx) < seqOf(events, stepStart));
        assertTrue(seqOf(events, stepStart) < seqOf(events, parallelStart));
        assertTrue(seqOf(events, parallelStart) < seqOf(events, reviewStart));
        assertTrue(seqOf(events, reviewStart) < seqOf(events, stepCompleted));
        assertTrue(seqOf(events, stepCompleted) < seqOf(events, summaryStart));
        assertTrue(seqOf(events, summaryCompleted) < seqOf(events, finalIdx));

        // Subtask projections carry subTaskId and parallel stepMode.
        List<Map<?, ?>> subTaskEvents = events.stream()
                .filter(event -> String.valueOf(event.get("eventType")).startsWith("SUBTASK_"))
                .toList();
        assertTrue(subTaskEvents.size() >= 4);
        assertTrue(subTaskEvents.stream().anyMatch(event -> "sub-a".equals(event.get("subTaskId"))));
        assertTrue(subTaskEvents.stream().anyMatch(event -> "sub-b".equals(event.get("subTaskId"))));
        assertEquals("PARALLEL_AGENTS", events.get(parallelStart).get("stepMode"));

        // FINAL_RESPONSE is the single terminal event with null step projections.
        assertEquals(1, AgentStage6TestSupport.finishedCount(channel.events()));
        Map<?, ?> terminal = events.get(finalIdx);
        assertNull(terminal.get("stepId"));
        assertNull(terminal.get("stepMode"));
        assertEquals("SUCCESS", terminal.get("completionStatus"));
    }

    private long seqOf(List<Map<?, ?>> events, int index) {
        return ((Number) events.get(index).get("sequence")).longValue();
    }
}
