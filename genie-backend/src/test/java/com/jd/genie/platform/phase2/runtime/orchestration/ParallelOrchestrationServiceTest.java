package com.jd.genie.platform.phase2.runtime.orchestration;

import com.jd.genie.agent.agent.AgentContext;
import com.jd.genie.agent.tool.BaseTool;
import com.jd.genie.agent.tool.ToolCollection;
import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.UserRole;
import com.jd.genie.platform.phase2.runtime.agent.AgentTaskResult;
import com.jd.genie.platform.phase2.runtime.agent.ConfiguredAgentExecutor;
import com.jd.genie.platform.phase2.runtime.plan.OrchestrationStep;
import com.jd.genie.platform.phase2.runtime.plan.OrchestrationSubTask;
import com.jd.genie.platform.phase2contract.dto.AgentRuntimeProfile;
import com.jd.genie.platform.phase2contract.enums.StepMode;
import com.jd.genie.platform.phase2contract.port.RuntimeToolCollectionPort;
import com.jd.genie.platform.phase2contract.support.FakeAgentRuntimeCatalogPort;
import com.jd.genie.platform.phase2contract.support.FakeSkillRuntimePort;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class ParallelOrchestrationServiceTest {
    private static final CurrentUser USER = new CurrentUser(
            "tenant-1", "user-1", "alice", "Alice", UserRole.USER
    );

    @Test
    void runsParallelSubTasksWithIsolatedResourcesAndWaitsBeforeNextTopLevelStep() throws Exception {
        FakeAgentRuntimeCatalogPort catalog = catalog("agent-a", "agent-b", "agent-c");
        FakeSkillRuntimePort skills = new FakeSkillRuntimePort();
        BaseTool agentATool = tool("skill-a");
        BaseTool agentBTool = tool("skill-b");
        skills.setRuntimeTools(USER, "agent-a", List.of(agentATool));
        skills.setRuntimeTools(USER, "agent-b", List.of(agentBTool));
        RecordingToolCollections tools = new RecordingToolCollections();
        ConfiguredAgentExecutor executor = mock(ConfiguredAgentExecutor.class);
        CountDownLatch parallelStarted = new CountDownLatch(2);
        CountDownLatch releaseParallel = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();
        List<String> completed = Collections.synchronizedList(new ArrayList<>());
        Map<String, AgentContext> contexts = new ConcurrentHashMap<>();

        doAnswer(invocation -> {
            AgentContext context = invocation.getArgument(0);
            contexts.put(context.getRequestId(), context);
            int current = active.incrementAndGet();
            maximum.accumulateAndGet(current, Math::max);
            try {
                if (context.getRequestId().startsWith("sub-")) {
                    parallelStarted.countDown();
                    assertTrue(releaseParallel.await(5, TimeUnit.SECONDS));
                } else {
                    assertEquals(0, active.get() - 1);
                }
                completed.add(context.getRequestId());
                return AgentTaskResult.success(context.getRequestId() + " output");
            } finally {
                active.decrementAndGet();
            }
        }).when(executor).execute(any(AgentContext.class), any(AgentRuntimeProfile.class), any(), anyInt());

        SerialOrchestrationService service = new SerialOrchestrationService(catalog, tools, skills, executor, 10);
        Thread runner = new Thread(() -> service.execute(USER, "query", List.of(
                parallelStep(),
                new OrchestrationStep("after", "agent-c", "consume parallel work", List.of("parallel"))
        ), (eventType, step, result, details) -> { }));
        runner.start();

        assertTrue(parallelStarted.await(5, TimeUnit.SECONDS));
        assertEquals(2, maximum.get());
        assertFalse(completed.contains("after"));
        releaseParallel.countDown();
        runner.join(5_000);

        assertFalse(runner.isAlive());
        assertTrue(completed.indexOf("after") > completed.indexOf("sub-a"));
        assertTrue(completed.indexOf("after") > completed.indexOf("sub-b"));
        AgentContext first = contexts.get("sub-a");
        AgentContext second = contexts.get("sub-b");
        assertNotSame(first, second);
        assertNotSame(first.getToolCollection(), second.getToolCollection());
        assertSame(agentATool, first.getToolCollection().getTool("skill-a"));
        assertSame(agentBTool, second.getToolCollection().getTool("skill-b"));
        assertFalse(first.getBasePrompt().contains("sub-b output"));
        assertFalse(second.getBasePrompt().contains("sub-a output"));
    }

    @Test
    void isolatesSameAgentSubTasksByContextToolCollectionAndRequestId() throws Exception {
        FakeAgentRuntimeCatalogPort catalog = catalog("agent-a");
        RecordingToolCollections tools = new RecordingToolCollections();
        ConfiguredAgentExecutor executor = mock(ConfiguredAgentExecutor.class);
        CountDownLatch started = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        Map<String, AgentContext> contexts = new ConcurrentHashMap<>();
        doAnswer(invocation -> {
            AgentContext context = invocation.getArgument(0);
            contexts.put(context.getRequestId(), context);
            started.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return AgentTaskResult.success(context.getRequestId() + " output");
        }).when(executor).execute(any(AgentContext.class), any(AgentRuntimeProfile.class), any(), anyInt());

        SerialOrchestrationService service = new SerialOrchestrationService(catalog, tools, executor, 10);
        Thread runner = new Thread(() -> service.execute(USER, "query", List.of(parallelStepWithSameAgent()),
                (eventType, step, result, details) -> { }));
        runner.start();
        assertTrue(started.await(5, TimeUnit.SECONDS));
        release.countDown();
        runner.join(5_000);

        AgentContext first = contexts.get("sub-a");
        AgentContext second = contexts.get("sub-b");
        assertNotSame(first, second);
        assertNotSame(first.getToolCollection(), second.getToolCollection());
        assertEquals("sub-a", first.getRequestId());
        assertEquals("sub-b", second.getRequestId());
        assertEquals(first.getSessionId(), second.getSessionId());
        assertNotEquals(first.getRequestId(), first.getSessionId());
    }

    @Test
    void capsOneParallelStepAtFourWorkersAndKeepsSubTaskInputsIndependent() throws Exception {
        FakeAgentRuntimeCatalogPort catalog = catalog("agent-a", "agent-b", "agent-c", "agent-d");
        RecordingToolCollections tools = new RecordingToolCollections();
        ConfiguredAgentExecutor executor = mock(ConfiguredAgentExecutor.class);
        CountDownLatch firstFourStarted = new CountDownLatch(4);
        CountDownLatch releaseFirstFour = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();
        Map<String, AgentContext> contexts = new ConcurrentHashMap<>();
        doAnswer(invocation -> {
            AgentContext context = invocation.getArgument(0);
            contexts.put(context.getRequestId(), context);
            if (context.getRequestId().equals("previous")) {
                return AgentTaskResult.success("previous output");
            }
            int current = active.incrementAndGet();
            maximum.accumulateAndGet(current, Math::max);
            try {
                firstFourStarted.countDown();
                assertTrue(releaseFirstFour.await(5, TimeUnit.SECONDS));
                return AgentTaskResult.success(context.getRequestId() + " output");
            } finally {
                active.decrementAndGet();
            }
        }).when(executor).execute(any(AgentContext.class), any(AgentRuntimeProfile.class), any(), anyInt());

        OrchestrationStep parallel = new OrchestrationStep(
                "parallel",
                StepMode.PARALLEL_AGENTS,
                "compare evidence",
                List.of("previous"),
                null,
                List.of(
                        new OrchestrationSubTask("sub-a", "agent-a", "inspect A"),
                        new OrchestrationSubTask("sub-b", "agent-b", "inspect B"),
                        new OrchestrationSubTask("sub-c", "agent-c", "inspect C"),
                        new OrchestrationSubTask("sub-d", "agent-d", "inspect D")
                )
        );
        SerialOrchestrationService service = new SerialOrchestrationService(catalog, tools, executor, 10);
        Thread runner = new Thread(() -> service.execute(USER, "query", List.of(
                new OrchestrationStep("previous", "agent-a", "produce prior evidence", List.of()),
                parallel
        ), (eventType, step, result, details) -> { }));
        runner.start();

        assertTrue(firstFourStarted.await(5, TimeUnit.SECONDS));
        assertEquals(4, maximum.get());
        releaseFirstFour.countDown();
        runner.join(5_000);

        assertFalse(runner.isAlive());
        AgentContext subA = contexts.get("sub-a");
        AgentContext subB = contexts.get("sub-b");
        assertTrue(subA.getBasePrompt().contains("compare evidence"));
        assertTrue(subA.getBasePrompt().contains("inspect A"));
        assertTrue(subA.getBasePrompt().contains("previous output"));
        assertFalse(subA.getBasePrompt().contains("inspect B"));
        assertFalse(subA.getBasePrompt().contains("sub-b output"));
        assertTrue(subB.getBasePrompt().contains("compare evidence"));
        assertTrue(subB.getBasePrompt().contains("inspect B"));
        assertFalse(subB.getBasePrompt().contains("inspect A"));
    }

    @Test
    void retriesOnlyTheFailedRetryableSubTaskOnceAfterReview() {
        FakeAgentRuntimeCatalogPort catalog = catalog("agent-a", "agent-b");
        ConfiguredAgentExecutor executor = mock(ConfiguredAgentExecutor.class);
        Map<String, AtomicInteger> calls = new ConcurrentHashMap<>();
        doAnswer(invocation -> {
            String requestId = invocation.getArgument(0, AgentContext.class).getRequestId();
            int call = calls.computeIfAbsent(requestId, ignored -> new AtomicInteger()).incrementAndGet();
            return "sub-a".equals(requestId) && call == 1
                    ? AgentTaskResult.failure("TOOL_TIMEOUT", true)
                    : AgentTaskResult.success(requestId + " output");
        }).when(executor).execute(any(AgentContext.class), any(AgentRuntimeProfile.class), any(), anyInt());
        OrchestrationModelPort reviewer = new OrchestrationModelPort() {
            @Override
            public com.jd.genie.platform.phase2.runtime.route.RouteDecision selectRoute(String query, String summary, List<com.jd.genie.platform.phase2contract.dto.AgentCapabilitySummary> candidates) {
                throw new UnsupportedOperationException();
            }

            @Override
            public com.jd.genie.platform.phase2.runtime.plan.OrchestrationPlan createPlan(String query, List<com.jd.genie.platform.phase2contract.dto.AgentCapabilitySummary> candidates, int attemptNo, Map<String, String> successes, Map<String, String> failures) {
                throw new UnsupportedOperationException();
            }

            @Override
            public String summarize(String query, Map<String, String> successes, Map<String, String> failures) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ReviewDecision review(String objective, String safeResult, String errorCode, boolean retryable, int retryNo) {
                return retryNo == 0 ? ReviewDecision.RETRY : ReviewDecision.COMPLETE;
            }
        };
        SerialOrchestrationService service = new SerialOrchestrationService(
                catalog, new RecordingToolCollections(), null, executor, 10, reviewer
        );
        List<String> events = new ArrayList<>();

        Map<String, AgentTaskResult> results = service.execute(USER, "query", List.of(parallelStep()),
                (eventType, step, result, details) -> events.add(eventType + ":" + details.getOrDefault("retryNo", "")));

        assertEquals(2, calls.get("sub-a").get());
        assertEquals(1, calls.get("sub-b").get());
        assertEquals(AgentTaskResult.Status.SUCCESS, results.get("parallel").status());
        // V2 ordering: PARALLEL_STARTED and SUBTASK_* events sit between STEP_STARTED
        // and the review cycle; only the failed retryable subTask is re-launched.
        assertEquals(1, events.stream().filter(event -> event.startsWith("STEP_STARTED:")).count());
        assertEquals(1, events.stream().filter(event -> event.startsWith("PARALLEL_STARTED:")).count());
        assertEquals(3, events.stream().filter(event -> event.startsWith("SUBTASK_STARTED:")).count());
        assertEquals(1, events.stream().filter(event -> event.startsWith("SUBTASK_FAILED:")).count());
        assertEquals(2, events.stream().filter(event -> event.startsWith("SUBTASK_COMPLETED:")).count());
        assertEquals(1, events.stream().filter("STEP_RETRY_STARTED:1"::equals).count());
        assertTrue(events.indexOf("STEP_REVIEW_STARTED:0") < events.indexOf("STEP_RETRY_STARTED:1"));
        assertTrue(events.indexOf("STEP_RETRY_STARTED:1") < events.indexOf("STEP_REVIEW_STARTED:1"));
        assertTrue(events.indexOf("STEP_REVIEW_STARTED:1") < events.indexOf("STEP_COMPLETED:"));
    }

    @Test
    void cancellationCancelsParallelGroupAndPreventsFollowingTopLevelStep() throws Exception {
        FakeAgentRuntimeCatalogPort catalog = catalog("agent-a", "agent-b", "agent-c");
        RecordingToolCollections tools = new RecordingToolCollections();
        ConfiguredAgentExecutor executor = mock(ConfiguredAgentExecutor.class);
        AtomicBoolean cancelled = new AtomicBoolean(false);
        CountDownLatch started = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        List<String> executions = Collections.synchronizedList(new ArrayList<>());
        doAnswer(invocation -> {
            AgentContext context = invocation.getArgument(0);
            executions.add(context.getRequestId());
            started.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return AgentTaskResult.success(context.getRequestId() + " output");
        }).when(executor).execute(any(AgentContext.class), any(AgentRuntimeProfile.class), any(), anyInt());

        SerialOrchestrationService service = new SerialOrchestrationService(catalog, tools, executor, 10);
        AtomicBoolean disconnected = new AtomicBoolean(false);
        Thread runner = new Thread(() -> {
            try {
                service.execute(USER, "query", List.of(
                        parallelStep(),
                        new OrchestrationStep("after", "agent-c", "must not start", List.of())
                ), (eventType, step, result, details) -> { }, cancelled::get);
            } catch (com.jd.genie.platform.agentbridge.AgentBridgeException error) {
                disconnected.set(error.getErrorCode() == com.jd.genie.platform.contract.MvpErrorCode.CLIENT_DISCONNECTED);
            }
        });
        runner.start();
        assertTrue(started.await(5, TimeUnit.SECONDS));
        cancelled.set(true);
        release.countDown();
        runner.join(5_000);

        assertTrue(disconnected.get());
        assertFalse(executions.contains("after"));
    }

    private static OrchestrationStep parallelStep() {
        return new OrchestrationStep(
                "parallel",
                StepMode.PARALLEL_AGENTS,
                "compare independent evidence",
                List.of(),
                null,
                List.of(
                        new OrchestrationSubTask("sub-a", "agent-a", "inspect source A"),
                        new OrchestrationSubTask("sub-b", "agent-b", "inspect source B")
                )
        );
    }

    private static OrchestrationStep parallelStepWithSameAgent() {
        return new OrchestrationStep(
                "parallel",
                StepMode.PARALLEL_AGENTS,
                "compare independent evidence",
                List.of(),
                null,
                List.of(
                        new OrchestrationSubTask("sub-a", "agent-a", "inspect source A"),
                        new OrchestrationSubTask("sub-b", "agent-a", "inspect source B")
                )
        );
    }

    private static FakeAgentRuntimeCatalogPort catalog(String... agentIds) {
        FakeAgentRuntimeCatalogPort catalog = new FakeAgentRuntimeCatalogPort();
        for (String agentId : agentIds) {
            catalog.registerProfile(new AgentRuntimeProfile(
                    agentId, 1L, agentId, "description", "prompt", "model", List.of(), List.of()
            ));
        }
        return catalog;
    }

    private static BaseTool tool(String name) {
        return new BaseTool() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public String getDescription() {
                return name;
            }

            @Override
            public Map<String, Object> toParams() {
                return Map.of();
            }

            @Override
            public Object execute(Object input) {
                return null;
            }
        };
    }

    private static final class RecordingToolCollections implements RuntimeToolCollectionPort {
        @Override
        public ToolCollection build(
                CurrentUser user,
                AgentRuntimeProfile profile,
                AgentContext context,
                List<BaseTool> additionalTools
        ) {
            ToolCollection collection = new ToolCollection();
            additionalTools.forEach(collection::addTool);
            return collection;
        }
    }
}
