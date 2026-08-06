package com.jd.genie.platform.phase2.runtime.orchestration;

import com.jd.genie.agent.agent.AgentContext;
import com.jd.genie.platform.agentbridge.AgentBridgeException;
import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.UserRole;
import com.jd.genie.platform.phase2.runtime.agent.AgentTaskResult;
import com.jd.genie.platform.phase2.runtime.agent.ConfiguredAgentExecutor;
import com.jd.genie.platform.phase2.runtime.plan.OrchestrationStep;
import com.jd.genie.platform.phase2contract.dto.AgentRuntimeProfile;
import com.jd.genie.platform.phase2contract.support.FakeAgentRuntimeCatalogPort;
import com.jd.genie.platform.phase2contract.support.FakeRuntimeToolCollectionPort;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class SerialOrchestrationServiceTest {
    private static final CurrentUser USER = new CurrentUser(
            "tenant-1", "user-1", "alice", "Alice", UserRole.USER
    );

    @Test
    void executesStepsInOrderAndTransfersOnlySuccessfulReferences() {
        FakeAgentRuntimeCatalogPort catalog = new FakeAgentRuntimeCatalogPort();
        catalog.registerProfile(profile("agent-a"));
        catalog.registerProfile(profile("agent-b"));
        FakeRuntimeToolCollectionPort tools = new FakeRuntimeToolCollectionPort();
        ConfiguredAgentExecutor executor = mock(ConfiguredAgentExecutor.class);
        List<String> executionOrder = new ArrayList<>();
        List<String> queries = new ArrayList<>();
        doAnswer(invocation -> {
            AgentContext context = invocation.getArgument(0);
            executionOrder.add(context.getRequestId());
            queries.add(context.getQuery());
            return AgentTaskResult.success(context.getRequestId().equals("step-1") ? "safe-result" : "final-result");
        }).when(executor).execute(any(), any(), any(), any(Integer.TYPE));
        List<String> eventTypes = new ArrayList<>();
        SerialOrchestrationService service = new SerialOrchestrationService(catalog, tools, executor, 10);

        Map<String, AgentTaskResult> results = service.execute(
                USER,
                "请用可用 Agent 各用一句话描述春天，然后汇总成一段话。",
                List.of(
                        new OrchestrationStep("step-1", "agent-a", "first", List.of()),
                        new OrchestrationStep("step-2", "agent-b", "second", List.of("step-1"))
                ),
                (eventType, step, result, details) -> eventTypes.add(eventType + ":" + step.stepId())
        );

        assertEquals(List.of("step-1", "step-2"), executionOrder);
        // Sub-agent must receive the step objective, not the parent orchestration query.
        org.junit.jupiter.api.Assertions.assertTrue(queries.get(0).contains("first"));
        org.junit.jupiter.api.Assertions.assertFalse(
                queries.get(0).contains("请用可用 Agent 各用一句话描述春天，然后汇总成一段话。")
        );
        org.junit.jupiter.api.Assertions.assertTrue(queries.get(1).contains("second"));
        org.junit.jupiter.api.Assertions.assertTrue(queries.get(1).contains("safe-result"));
        assertEquals(List.of("agent-a", "agent-b"), tools.getCalls().stream()
                .map(FakeRuntimeToolCollectionPort.CallRecord::agentId)
                .toList());
        assertEquals(List.of(
                "STEP_STARTED:step-1", "STEP_COMPLETED:step-1",
                "STEP_STARTED:step-2", "STEP_COMPLETED:step-2"
        ), eventTypes);
        assertEquals("safe-result", results.get("step-1").output());
        assertEquals("final-result", results.get("step-2").output());
        assertEquals(null, service.runningStepId());
    }

    @Test
    void neverRunsMoreThanOneBusinessAgentAtATime() {
        FakeAgentRuntimeCatalogPort catalog = new FakeAgentRuntimeCatalogPort();
        catalog.registerProfile(profile("agent-a"));
        catalog.registerProfile(profile("agent-b"));
        FakeRuntimeToolCollectionPort tools = new FakeRuntimeToolCollectionPort();
        ConfiguredAgentExecutor executor = mock(ConfiguredAgentExecutor.class);
        SerialOrchestrationService service = new SerialOrchestrationService(catalog, tools, executor, 10);
        List<String> runningStepsObservedByExecutor = new ArrayList<>();
        doAnswer(invocation -> {
            runningStepsObservedByExecutor.add(service.runningStepId());
            return AgentTaskResult.success("safe");
        }).when(executor).execute(any(), any(), any(), any(Integer.TYPE));

        service.execute(
                USER,
                "question",
                List.of(
                        new OrchestrationStep("step-1", "agent-a", "first", List.of()),
                        new OrchestrationStep("step-2", "agent-b", "second", List.of())
                ),
                (eventType, step, result, details) -> { }
        );

        assertEquals(List.of("step-1", "step-2"), runningStepsObservedByExecutor);
        assertEquals(null, service.runningStepId());
    }

    @Test
    void failureSkipsRemainingStepsWithoutLoadingTheirRuntime() {
        FakeAgentRuntimeCatalogPort catalog = new FakeAgentRuntimeCatalogPort();
        catalog.registerProfile(profile("agent-a"));
        catalog.registerProfile(profile("agent-b"));
        FakeRuntimeToolCollectionPort tools = new FakeRuntimeToolCollectionPort();
        ConfiguredAgentExecutor executor = mock(ConfiguredAgentExecutor.class);
        doAnswer(invocation -> AgentTaskResult.failure("EXECUTION_ERROR", true))
                .when(executor).execute(any(), any(), any(), any(Integer.TYPE));
        List<String> eventTypes = new ArrayList<>();
        SerialOrchestrationService service = new SerialOrchestrationService(catalog, tools, executor, 10);

        Map<String, AgentTaskResult> results = service.execute(
                USER,
                "question",
                List.of(
                        new OrchestrationStep("step-1", "agent-a", "first", List.of()),
                        new OrchestrationStep("step-2", "agent-b", "second", List.of())
                ),
                (eventType, step, result, details) -> eventTypes.add(eventType + ":" + step.stepId())
        );

        assertEquals(List.of("agent-a"), catalog.getCalls().stream()
                .map(FakeAgentRuntimeCatalogPort.CallRecord::agentId)
                .toList());
        assertEquals(List.of("agent-a"), tools.getCalls().stream()
                .map(FakeRuntimeToolCollectionPort.CallRecord::agentId)
                .toList());
        assertEquals(List.of(
                "STEP_STARTED:step-1", "STEP_FAILED:step-1", "STEP_SKIPPED:step-2"
        ), eventTypes);
        assertEquals(AgentTaskResult.Status.FAILURE, results.get("step-2").status());
        assertEquals("EXECUTION_ERROR", results.get("step-2").errorCode());
        assertEquals(null, service.runningStepId());
    }

    @Test
    void reusesOnlyMatchingSuccessfulResultWithinTheSameRequest() {
        FakeAgentRuntimeCatalogPort catalog = new FakeAgentRuntimeCatalogPort();
        catalog.registerProfile(profile("agent-a"));
        FakeRuntimeToolCollectionPort tools = new FakeRuntimeToolCollectionPort();
        ConfiguredAgentExecutor executor = mock(ConfiguredAgentExecutor.class);
        doAnswer(invocation -> AgentTaskResult.success("reusable result"))
                .when(executor).execute(any(), any(), any(), any(Integer.TYPE));
        SerialOrchestrationService service = new SerialOrchestrationService(catalog, tools, executor, 10);
        Map<String, AgentTaskResult> reusableResults = new java.util.LinkedHashMap<>();
        List<String> eventDetails = new ArrayList<>();

        service.execute(
                USER, "question", List.of(new OrchestrationStep("first", "agent-a", "same objective", List.of())),
                (eventType, step, result, details) -> eventDetails.add(eventType + ":" + details.getOrDefault("reasonCode", "")),
                () -> false, reusableResults
        );
        service.execute(
                USER, "question", List.of(new OrchestrationStep("second", "agent-a", "same objective", List.of())),
                (eventType, step, result, details) -> eventDetails.add(eventType + ":" + details.getOrDefault("reasonCode", "")),
                () -> false, reusableResults
        );

        org.mockito.Mockito.verify(executor, org.mockito.Mockito.times(1))
                .execute(any(), any(), any(), any(Integer.TYPE));
        assertEquals(List.of("STEP_STARTED:", "STEP_COMPLETED:", "STEP_COMPLETED:REUSED"), eventDetails);
    }

    @Test
    void doesNotReuseWhenAgentVersionOrDependencyOutputChanges() {
        FakeAgentRuntimeCatalogPort catalog = new FakeAgentRuntimeCatalogPort();
        catalog.registerProfile(profile("agent-a"));
        FakeRuntimeToolCollectionPort tools = new FakeRuntimeToolCollectionPort();
        ConfiguredAgentExecutor executor = mock(ConfiguredAgentExecutor.class);
        doAnswer(invocation -> AgentTaskResult.success("result-" + invocation.getArgument(0, AgentContext.class).getRequestId()))
                .when(executor).execute(any(), any(), any(), any(Integer.TYPE));
        SerialOrchestrationService service = new SerialOrchestrationService(catalog, tools, executor, 10);
        Map<String, AgentTaskResult> reusableResults = new java.util.LinkedHashMap<>();

        service.execute(USER, "query-one", List.of(new OrchestrationStep("first", "agent-a", "same", List.of())),
                (eventType, step, result, details) -> { }, () -> false, reusableResults);
        catalog.registerProfile(new AgentRuntimeProfile(
                "agent-a", 2L, "agent-a", "description", "system prompt", "model", List.of(), List.of()
        ));
        service.execute(USER, "query-two", List.of(new OrchestrationStep("second", "agent-a", "same", List.of())),
                (eventType, step, result, details) -> { }, () -> false, reusableResults);

        org.mockito.Mockito.verify(executor, org.mockito.Mockito.times(2))
                .execute(any(), any(), any(), any(Integer.TYPE));
    }

    @Test
    void cancellationStopsBeforeLaunchingTheNextBusinessAgent() {
        FakeAgentRuntimeCatalogPort catalog = new FakeAgentRuntimeCatalogPort();
        catalog.registerProfile(profile("agent-a"));
        catalog.registerProfile(profile("agent-b"));
        FakeRuntimeToolCollectionPort tools = new FakeRuntimeToolCollectionPort();
        ConfiguredAgentExecutor executor = mock(ConfiguredAgentExecutor.class);
        AtomicBoolean cancelled = new AtomicBoolean(false);
        doAnswer(invocation -> {
            cancelled.set(true);
            return AgentTaskResult.success("first result");
        }).when(executor).execute(any(), any(), any(), any(Integer.TYPE));
        SerialOrchestrationService service = new SerialOrchestrationService(catalog, tools, executor, 10);

        AgentBridgeException error = assertThrows(
                AgentBridgeException.class,
                () -> service.execute(
                        USER,
                        "question",
                        List.of(
                                new OrchestrationStep("step-1", "agent-a", "first", List.of()),
                                new OrchestrationStep("step-2", "agent-b", "second", List.of())
                        ),
                        (eventType, step, result, details) -> { },
                        cancelled::get
                )
        );

        assertEquals(com.jd.genie.platform.contract.MvpErrorCode.CLIENT_DISCONNECTED, error.getErrorCode());
        assertEquals(List.of("agent-a"), catalog.getCalls().stream()
                .map(FakeAgentRuntimeCatalogPort.CallRecord::agentId)
                .toList());
        assertEquals(null, service.runningStepId());
    }

    private AgentRuntimeProfile profile(String agentId) {
        return new AgentRuntimeProfile(
                agentId, 1L, agentId, "description", "system prompt", "model", List.of(), List.of()
        );
    }
}
