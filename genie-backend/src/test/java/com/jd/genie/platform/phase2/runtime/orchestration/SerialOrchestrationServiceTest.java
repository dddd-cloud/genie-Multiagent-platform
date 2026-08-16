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
import static org.junit.jupiter.api.Assertions.assertTrue;
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
        assertTrue(queries.get(0).contains("first"));
        assertTrue(queries.get(0).contains("请用可用 Agent 各用一句话描述春天，然后汇总成一段话。"));
        assertTrue(queries.get(0).contains("只用于限定主题"));
        assertTrue(queries.get(0).contains("禁止整题作答"));
        assertTrue(queries.get(1).contains("second"));
        assertTrue(queries.get(1).contains("safe-result"));
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
    void injectsLongTermMemoryAndConversationSummaryIntoSpecialistQuery() {
        FakeAgentRuntimeCatalogPort catalog = new FakeAgentRuntimeCatalogPort();
        catalog.registerProfile(profile("agent-a"));
        FakeRuntimeToolCollectionPort tools = new FakeRuntimeToolCollectionPort();
        ConfiguredAgentExecutor executor = mock(ConfiguredAgentExecutor.class);
        List<String> queries = new ArrayList<>();
        doAnswer(invocation -> {
            AgentContext context = invocation.getArgument(0);
            queries.add(context.getQuery());
            return AgentTaskResult.success("ok");
        }).when(executor).execute(any(), any(), any(), any(Integer.TYPE));
        SerialOrchestrationService service = new SerialOrchestrationService(catalog, tools, executor, 10);

        service.execute(
                USER,
                "我是谁？",
                UntrustedLocalContext.body("林晓，杭州 Java 后端", "当前目标：测试记忆"),
                List.of(new OrchestrationStep("step-1", "agent-a", "根据记忆回答身份", List.of())),
                (eventType, step, result, details) -> { },
                () -> false,
                new java.util.LinkedHashMap<>(),
                null,
                1
        );

        assertEquals(1, queries.size());
        assertTrue(queries.get(0).contains("UNTRUSTED_LOCAL_CONTEXT"));
        assertTrue(queries.get(0).contains("林晓，杭州 Java 后端"));
        assertTrue(queries.get(0).contains("当前目标：测试记忆"));
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

    @Test
    void specialistQueryIncludesRecentConversationHistory() {
        FakeAgentRuntimeCatalogPort catalog = new FakeAgentRuntimeCatalogPort();
        catalog.registerProfile(profile("agent-a"));
        FakeRuntimeToolCollectionPort tools = new FakeRuntimeToolCollectionPort();
        ConfiguredAgentExecutor executor = mock(ConfiguredAgentExecutor.class);
        List<String> queries = new ArrayList<>();
        doAnswer(invocation -> {
            queries.add(invocation.getArgument(0, AgentContext.class).getQuery());
            return AgentTaskResult.success("ok");
        }).when(executor).execute(any(), any(), any(), any(Integer.TYPE));
        SerialOrchestrationService service = new SerialOrchestrationService(catalog, tools, executor, 10);

        service.execute(
                USER,
                "那竞品呢",
                "user: 茅台市场规模多大\nassistant: 约三千亿。",
                "",
                List.of(new OrchestrationStep("step-1", "agent-a", "分析竞品", List.of())),
                (eventType, step, result, details) -> { },
                () -> false,
                new java.util.LinkedHashMap<>(),
                null,
                1,
                null
        );

        assertEquals(1, queries.size());
        assertTrue(queries.get(0).contains("那竞品呢"));
        assertTrue(queries.get(0).contains("茅台市场规模多大"));
        assertTrue(queries.get(0).contains("约三千亿。"));
        assertTrue(queries.get(0).contains("近期对话"));
        assertTrue(queries.get(0).contains("分析竞品"));
    }

    @Test
    void collectsNonInternalProductFilesAsDeliverables() {
        FakeAgentRuntimeCatalogPort catalog = new FakeAgentRuntimeCatalogPort();
        catalog.registerProfile(profile("agent-a"));
        FakeRuntimeToolCollectionPort tools = new FakeRuntimeToolCollectionPort();
        ConfiguredAgentExecutor executor = mock(ConfiguredAgentExecutor.class);
        doAnswer(invocation -> {
            AgentContext context = invocation.getArgument(0);
            context.getProductFiles().add(com.jd.genie.agent.dto.File.builder()
                    .fileName("page.html")
                    .ossUrl("http://127.0.0.1:1601/v1/file_tool/download/r/page.html")
                    .domainUrl("http://127.0.0.1:1601/v1/file_tool/preview/r/page.html")
                    .fileSize(12)
                    .isInternalFile(false)
                    .build());
            return AgentTaskResult.success("uploaded");
        }).when(executor).execute(any(), any(), any(), any(Integer.TYPE));
        java.util.List<com.jd.genie.agent.dto.File> captured = new ArrayList<>();
        SerialOrchestrationService service = new SerialOrchestrationService(catalog, tools, executor, 10);

        service.execute(
                USER,
                "生成一个品牌落地页 html",
                List.of(new OrchestrationStep("step-1", "agent-a", "写 html", List.of())),
                new OrchestrationEventSink() {
                    @Override
                    public void emit(String eventType, OrchestrationStep step, AgentTaskResult result, Map<String, Object> details) {
                    }

                    @Override
                    public void acceptDeliverables(java.util.List<com.jd.genie.agent.dto.File> files) {
                        captured.addAll(files);
                    }
                }
        );

        assertEquals(1, captured.size());
        assertEquals("page.html", captured.get(0).getFileName());
    }

    private AgentRuntimeProfile profile(String agentId) {
        return new AgentRuntimeProfile(
                agentId, 1L, agentId, "description", "system prompt", "model", List.of(), List.of()
        );
    }
}
