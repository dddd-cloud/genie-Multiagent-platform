package com.jd.genie.platform.phase2.runtime.agent;

import com.jd.genie.agent.agent.AgentContext;
import com.jd.genie.agent.agent.ReactImplAgent;
import com.jd.genie.agent.printer.Printer;
import com.jd.genie.agent.tool.ToolCollection;
import com.jd.genie.agent.util.SpringContextHolder;
import com.jd.genie.config.GenieConfig;
import com.jd.genie.platform.phase2contract.dto.AgentRuntimeProfile;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConfiguredReactAgentFactoryTest {
    private final ConfiguredReactAgentFactory factory = new ConfiguredReactAgentFactory();
    private final AgentRuntimeProfile profile = new AgentRuntimeProfile(
            "agent-1", 1L, "Agent", "description", "frozen prompt", "frozen-model", List.of(), List.of()
    );

    @Test
    void freezesProfilePromptModelToolsAndBoundedStepLimit() {
        AgentContext context = AgentContext.builder()
                .requestId("request-1")
                .query("query")
                .task("task")
                .dateInfo("")
                .basePrompt("")
                .build();
        ToolCollection tools = new ToolCollection();
        Printer printer = new ConfiguredAgentPrinter();
        context.setToolCollection(tools);

        ApplicationContext springApplicationContext = applicationContext();
        installApplicationContext(springApplicationContext);
        try {
            ReactImplAgent agent = factory.create(context, profile, printer, 99);

            assertEquals(true, agent.getSystemPrompt().startsWith("frozen prompt"));
            assertEquals(true, agent.getSystemPrompt().contains("\"status\":\"SUCCESS\""));
            assertEquals(agent.getSystemPrompt(), agent.getSystemPromptSnapshot());
            // Frozen per-step nudge: never re-inject the full system prompt each step.
            assertEquals(agent.getNextStepPrompt(), agent.getNextStepPromptSnapshot());
            assertEquals(true, agent.getNextStepPrompt().contains("After the latest tool result"));
            assertEquals("frozen-model", agent.getLlm().getModel());
            assertEquals(20, agent.getMaxSteps());
            assertSame(tools, agent.getAvailableTools());
            assertSame(printer, context.getPrinter());
        } finally {
            installApplicationContext(null);
        }
    }

    private ApplicationContext applicationContext() {
        GenieConfig config = new GenieConfig() {
            @Override
            public Integer getReactMaxSteps() {
                return 40;
            }

            @Override
            public String getReactModelName() {
                return "frozen-model";
            }
        };
        config.setLLMSettingsMap("""
                {"frozen-model":{"model":"frozen-model","maxTokens":128,"temperature":0,
                "maxInputTokens":1024,"functionCallType":"function_call"}}
                """);
        ApplicationContext context = mock(ApplicationContext.class);
        when(context.getBean(GenieConfig.class)).thenReturn(config);
        return context;
    }

    private void installApplicationContext(ApplicationContext context) {
        new SpringContextHolder().setApplicationContext(context);
    }

    @Test
    void appliesDefaultFrozenStepLimit() {
        AgentContext context = AgentContext.builder()
                .requestId("request-1")
                .query("query")
                .task("task")
                .dateInfo("")
                .basePrompt("")
                .build();
        context.setToolCollection(new ToolCollection());

        ApplicationContext springApplicationContext = applicationContext();
        installApplicationContext(springApplicationContext);
        try {
            ReactImplAgent agent = factory.create(context, profile, new ConfiguredAgentPrinter(), 0);

            assertEquals(10, agent.getMaxSteps());
            assertNotNull(agent.getAvailableTools());
        } finally {
            installApplicationContext(null);
        }
    }
}
