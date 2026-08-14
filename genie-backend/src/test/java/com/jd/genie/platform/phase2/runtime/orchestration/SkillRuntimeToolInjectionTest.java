package com.jd.genie.platform.phase2.runtime.orchestration;

import com.jd.genie.agent.agent.AgentContext;
import com.jd.genie.agent.tool.BaseTool;
import com.jd.genie.agent.tool.ToolCollection;
import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.UserRole;
import com.jd.genie.platform.phase2.runtime.agent.AgentTaskResult;
import com.jd.genie.platform.phase2.runtime.agent.ConfiguredAgentExecutor;
import com.jd.genie.platform.phase2.runtime.plan.OrchestrationStep;
import com.jd.genie.platform.phase2contract.dto.AgentRuntimeProfile;
import com.jd.genie.platform.phase2contract.port.RuntimeToolCollectionPort;
import com.jd.genie.platform.phase2contract.support.FakeAgentRuntimeCatalogPort;
import com.jd.genie.platform.phase2contract.support.FakeSkillRuntimePort;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class SkillRuntimeToolInjectionTest {
    private static final CurrentUser USER = new CurrentUser(
            "tenant-1", "user-1", "alice", "Alice", UserRole.USER
    );

    @Test
    void injectsEachConfiguredAgentsSkillToolsIntoItsOwnStepToolCollection() {
        FakeAgentRuntimeCatalogPort catalog = new FakeAgentRuntimeCatalogPort();
        catalog.registerProfile(profile("agent-a"));
        catalog.registerProfile(profile("agent-b"));

        FakeSkillRuntimePort skillRuntime = new FakeSkillRuntimePort();
        BaseTool agentATool = tool("skill-a-tool");
        BaseTool agentBTool = tool("skill-b-tool");
        skillRuntime.setRuntimeTools(USER, "agent-a", List.of(agentATool));
        skillRuntime.setRuntimeTools(USER, "agent-b", List.of(agentBTool));

        RecordingToolCollectionPort toolCollections = new RecordingToolCollectionPort();
        ConfiguredAgentExecutor executor = mock(ConfiguredAgentExecutor.class);
        Map<String, AgentContext> executionContexts = new LinkedHashMap<>();
        doAnswer(invocation -> {
            AgentContext context = invocation.getArgument(0);
            executionContexts.put(context.getRequestId(), context);
            return AgentTaskResult.success(context.getRequestId() + " result");
        }).when(executor).execute(any(AgentContext.class), any(AgentRuntimeProfile.class), any(), anyInt());

        SerialOrchestrationService service = new SerialOrchestrationService(
                catalog, toolCollections, skillRuntime, executor, 10
        );
        service.execute(USER, "question", List.of(
                new OrchestrationStep("step-a", "agent-a", "first objective", List.of()),
                new OrchestrationStep("step-b", "agent-b", "second objective", List.of())
        ), (eventType, step, result, details) -> { });

        assertEquals(List.of("agent-a", "agent-b"), skillRuntime.getCalls().stream()
                .filter(call -> call.type() == FakeSkillRuntimePort.CallType.BUILD_RUNTIME_TOOLS)
                .map(FakeSkillRuntimePort.CallRecord::agentId)
                .toList());
        assertEquals(List.of(
                List.of("skill-a-tool"),
                List.of("skill-b-tool")
        ), toolCollections.calls().stream()
                .map(call -> call.additionalTools().stream().map(BaseTool::getName).toList())
                .toList());

        AgentContext firstContext = executionContexts.get("step-a");
        AgentContext secondContext = executionContexts.get("step-b");
        assertNotSame(firstContext, secondContext);
        assertNotSame(firstContext.getToolCollection(), secondContext.getToolCollection());
        assertSame(toolCollections.calls().get(0).collection(), firstContext.getToolCollection());
        assertSame(toolCollections.calls().get(1).collection(), secondContext.getToolCollection());
        assertSame(agentATool, firstContext.getToolCollection().getTool("skill-a-tool"));
        assertSame(agentBTool, secondContext.getToolCollection().getTool("skill-b-tool"));
    }

    private static AgentRuntimeProfile profile(String agentId) {
        return new AgentRuntimeProfile(
                agentId, 1L, agentId, "description", "system prompt", "model", List.of(), List.of()
        );
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

    private static final class RecordingToolCollectionPort implements RuntimeToolCollectionPort {
        private final List<BuildCall> calls = new ArrayList<>();

        @Override
        public ToolCollection build(
                CurrentUser user,
                AgentRuntimeProfile profile,
                AgentContext context,
                List<BaseTool> additionalTools
        ) {
            ToolCollection collection = new ToolCollection();
            List<BaseTool> copiedTools = List.copyOf(additionalTools);
            copiedTools.forEach(collection::addTool);
            calls.add(new BuildCall(profile.agentId(), copiedTools, collection));
            return collection;
        }

        List<BuildCall> calls() {
            return List.copyOf(calls);
        }
    }

    private record BuildCall(String agentId, List<BaseTool> additionalTools, ToolCollection collection) {
    }
}
