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
import java.util.concurrent.ConcurrentHashMap;

import static com.jd.genie.platform.phase2.runtime.orchestration.AgentStage6TestSupport.catalog;
import static com.jd.genie.platform.phase2.runtime.orchestration.AgentStage6TestSupport.parallelStep;
import static com.jd.genie.platform.phase2.runtime.orchestration.AgentStage6TestSupport.singleStep;
import static com.jd.genie.platform.phase2.runtime.orchestration.AgentStage6TestSupport.subTask;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class SubTaskInputIsolationTest {

    @Test
    void eachSubTaskSeesOnlyTheStepObjectiveItsOwnObjectiveAndReferencedResults() {
        FakeAgentRuntimeCatalogPort catalog = catalog("agent-a", "agent-b");
        ConfiguredAgentExecutor executor = mock(ConfiguredAgentExecutor.class);
        Map<String, AgentContext> contexts = new ConcurrentHashMap<>();

        doAnswer(invocation -> {
            AgentContext context = invocation.getArgument(0);
            contexts.put(context.getRequestId(), context);
            return AgentTaskResult.success(context.getRequestId() + " output");
        }).when(executor).execute(any(AgentContext.class), any(AgentRuntimeProfile.class), any(), anyInt());

        SerialOrchestrationService service = new SerialOrchestrationService(
                catalog, new FakeRuntimeToolCollectionPort(), executor, 10
        );
        service.execute(
                AgentStage6TestSupport.USER,
                "query",
                List.of(
                        singleStep("prepare", "agent-a", "prepare shared evidence"),
                        parallelStep("parallel", List.of(
                                subTask("sub-a", "agent-a", "inspect source A"),
                                subTask("sub-b", "agent-b", "inspect source B")
                        ), List.of("prepare"))
                ),
                (eventType, step, result, details) -> { }
        );

        assertEquals(3, contexts.size());
        AgentContext subA = contexts.get("sub-a");
        AgentContext subB = contexts.get("sub-b");

        assertTrue(subA.getBasePrompt().contains("compare independent evidence"));
        assertTrue(subA.getBasePrompt().contains("inspect source A"));
        assertTrue(subA.getBasePrompt().contains("prepare output"));
        assertFalse(subA.getBasePrompt().contains("inspect source B"));

        assertTrue(subB.getBasePrompt().contains("compare independent evidence"));
        assertTrue(subB.getBasePrompt().contains("inspect source B"));
        assertTrue(subB.getBasePrompt().contains("prepare output"));
        assertFalse(subB.getBasePrompt().contains("inspect source A"));

        // Task holds the combined objective for the subTask, not the parent query.
        assertTrue(subA.getTask().contains("所属顶层步骤目标"));
        assertTrue(subA.getTask().contains("inspect source A"));
    }
}
