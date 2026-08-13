package com.jd.genie.platform.phase2.runtime.orchestration;

import com.jd.genie.agent.agent.AgentContext;
import com.jd.genie.agent.printer.Printer;
import com.jd.genie.agent.tool.BaseTool;
import com.jd.genie.agent.tool.ToolCollection;
import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.phase2.runtime.agent.AgentTaskResult;
import com.jd.genie.platform.phase2.runtime.agent.ConfiguredAgentExecutor;
import com.jd.genie.platform.phase2contract.dto.AgentRuntimeProfile;
import com.jd.genie.platform.phase2contract.port.RuntimeToolCollectionPort;
import com.jd.genie.platform.phase2contract.support.FakeAgentRuntimeCatalogPort;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.jd.genie.platform.phase2.runtime.orchestration.AgentStage6TestSupport.catalog;
import static com.jd.genie.platform.phase2.runtime.orchestration.AgentStage6TestSupport.parallelStep;
import static com.jd.genie.platform.phase2.runtime.orchestration.AgentStage6TestSupport.subTask;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class ParallelSameAgentIsolationTest {

    @Test
    void sameAgentSubTasksRunAsFullyIndependentExecutionUnits() throws Exception {
        FakeAgentRuntimeCatalogPort catalog = catalog("agent-a");
        ConfiguredAgentExecutor executor = mock(ConfiguredAgentExecutor.class);
        Map<String, AgentContext> contexts = new ConcurrentHashMap<>();
        Map<String, Printer> printers = new ConcurrentHashMap<>();
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch bothStarted = new CountDownLatch(2);

        doAnswer(invocation -> {
            AgentContext context = invocation.getArgument(0);
            contexts.put(context.getRequestId(), context);
            printers.put(context.getRequestId(), invocation.getArgument(2, Printer.class));
            bothStarted.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return AgentTaskResult.success(context.getRequestId() + " output");
        }).when(executor).execute(any(AgentContext.class), any(AgentRuntimeProfile.class), any(Printer.class), anyInt());

        SerialOrchestrationService service = new SerialOrchestrationService(
                catalog, new IsolatingToolCollections(), executor, 10
        );
        Thread runner = new Thread(() -> service.execute(
                AgentStage6TestSupport.USER,
                "query",
                List.of(parallelStep("parallel", List.of(
                        subTask("sub-a", "agent-a", "angle A"),
                        subTask("sub-b", "agent-a", "angle B")
                ))),
                (eventType, step, result, details) -> { }
        ));
        runner.start();

        assertTrue(bothStarted.await(5, TimeUnit.SECONDS));
        release.countDown();
        runner.join(5_000);
        assertTrue(!runner.isAlive());

        assertEquals(2, contexts.size());
        AgentContext first = contexts.get("sub-a");
        AgentContext second = contexts.get("sub-b");
        assertNotSame(first, second);
        assertNotSame(first.getToolCollection(), second.getToolCollection());
        assertNotSame(printers.get("sub-a"), printers.get("sub-b"));
        assertTrue(first.getBasePrompt().contains("angle A"));
        assertTrue(!first.getBasePrompt().contains("angle B"));
        assertTrue(second.getBasePrompt().contains("angle B"));
        assertTrue(!second.getBasePrompt().contains("angle A"));
    }

    /** Frozen-port fake that builds a fresh ToolCollection per execution unit. */
    private static final class IsolatingToolCollections implements RuntimeToolCollectionPort {
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
