package com.jd.genie.platform.agentbridge;

import com.jd.genie.agent.agent.AgentContext;
import com.jd.genie.agent.agent.ExecutorAgent;
import com.jd.genie.agent.agent.PlanningAgent;
import com.jd.genie.agent.agent.ReactImplAgent;
import com.jd.genie.agent.agent.SummaryAgent;
import com.jd.genie.agent.dto.Memory;
import com.jd.genie.agent.dto.Message;
import com.jd.genie.agent.dto.TaskSummaryResult;
import com.jd.genie.agent.enums.RoleType;
import com.jd.genie.agent.printer.Printer;
import com.jd.genie.agent.tool.ToolCollection;
import com.jd.genie.config.GenieConfig;
import com.jd.genie.model.req.AgentRequest;
import com.jd.genie.service.SopRecallService;
import com.jd.genie.service.impl.PlanSolveHandlerImpl;
import com.jd.genie.service.impl.ReactHandlerImpl;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReActPlanHandlerHistoryRegressionTest {

    @Test
    void reactHandlerInjectsTrustedHistoryBeforeCurrentExecution() {
        Memory reactMemory = new Memory();
        Printer printer = mock(Printer.class);

        try (
                MockedConstruction<ReactImplAgent> reactAgents = Mockito.mockConstruction(
                        ReactImplAgent.class,
                        (agent, context) -> {
                            when(agent.getMemory()).thenReturn(reactMemory);
                            when(agent.run("当前请求")).thenReturn("当前执行结果");
                        }
                );
                MockedConstruction<SummaryAgent> summaries = Mockito.mockConstruction(
                        SummaryAgent.class,
                        (summary, context) -> {
                            when(summary.getSystemPrompt()).thenReturn("{{query}}");
                            when(summary.summaryTaskResult(anyList(), eq("当前请求"))).thenReturn(summary());
                        }
                )
        ) {
            new ReactHandlerImpl().handle(agentContext(printer), request());

            assertHistory(reactMemory);
            verify(reactAgents.constructed().get(0)).run("当前请求");
            verify(printer).send(eq("result"), Mockito.any());
        }
    }

    @Test
    void planHandlerInjectsTrustedHistoryIntoPlanningMemory() throws Exception {
        Memory planningMemory = new Memory();
        Memory executorMemory = new Memory();
        Printer printer = mock(Printer.class);
        GenieConfig config = mock(GenieConfig.class);
        SopRecallService sopRecallService = mock(SopRecallService.class);
        when(config.getPlannerMaxSteps()).thenReturn(0);
        when(sopRecallService.sopRecall(anyString(), anyString())).thenReturn(null);

        PlanSolveHandlerImpl handler = new PlanSolveHandlerImpl();
        setField(handler, "genieConfig", config);
        setField(handler, "sopRecallService", sopRecallService);

        try (
                MockedConstruction<PlanningAgent> planningAgents = Mockito.mockConstruction(
                        PlanningAgent.class,
                        (agent, context) -> {
                            when(agent.getMemory()).thenReturn(planningMemory);
                            when(agent.run(anyString())).thenReturn("finish");
                        }
                );
                MockedConstruction<ExecutorAgent> executorAgents = Mockito.mockConstruction(
                        ExecutorAgent.class,
                        (agent, context) -> {
                            when(agent.getMemory()).thenReturn(executorMemory);
                            when(agent.run(anyString())).thenReturn("当前执行结果");
                        }
                );
                MockedConstruction<SummaryAgent> summaries = Mockito.mockConstruction(
                        SummaryAgent.class,
                        (summary, context) -> {
                            when(summary.getSystemPrompt()).thenReturn("{{query}}");
                            when(summary.summaryTaskResult(anyList(), eq("当前请求"))).thenReturn(summary());
                        }
                )
        ) {
            handler.handle(agentContext(printer), request());

            assertHistory(planningMemory);
            assertEquals(0, executorMemory.size());
            verify(planningAgents.constructed().get(0)).run("当前请求");
            verify(printer).send(eq("result"), Mockito.any());
        }
    }

    private AgentContext agentContext(Printer printer) {
        return AgentContext.builder()
                .requestId("trace-1")
                .query("当前请求")
                .printer(printer)
                .toolCollection(new ToolCollection())
                .productFiles(new ArrayList<>())
                .taskProductFiles(new ArrayList<>())
                .build();
    }

    private AgentRequest request() {
        return AgentRequest.builder()
                .requestId("trace-1")
                .query("当前请求")
                .messages(List.of(
                        AgentRequest.Message.builder().role("user").content("第一轮问题").build(),
                        AgentRequest.Message.builder().role("assistant").content("第一轮回答").build(),
                        AgentRequest.Message.builder().role("tool").content("不得注入").build()
                ))
                .build();
    }

    private TaskSummaryResult summary() {
        return TaskSummaryResult.builder().taskSummary("总结").build();
    }

    private void assertHistory(Memory memory) {
        assertEquals(List.of(RoleType.USER, RoleType.ASSISTANT), memory.getMessages().stream()
                .map(Message::getRole)
                .toList());
        assertEquals(List.of("第一轮问题", "第一轮回答"), memory.getMessages().stream()
                .map(Message::getContent)
                .toList());
    }

    private void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
