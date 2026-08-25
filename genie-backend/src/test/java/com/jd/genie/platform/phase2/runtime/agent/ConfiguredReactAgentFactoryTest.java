package com.jd.genie.platform.phase2.runtime.agent;

import com.jd.genie.agent.agent.AgentContext;
import com.jd.genie.agent.agent.ReactImplAgent;
import com.jd.genie.agent.printer.Printer;
import com.jd.genie.agent.tool.ToolCollection;
import com.jd.genie.agent.util.SpringContextHolder;
import com.jd.genie.config.GenieConfig;
import com.jd.genie.platform.phase2contract.dto.AgentRuntimeProfile;
import com.jd.genie.platform.phase2contract.dto.AgentRuntimeSkill;
import com.jd.genie.platform.phase2contract.dto.SkillEntrypointView;
import com.jd.genie.platform.phase2contract.enums.SkillEntrypointRuntime;
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
            assertEquals("frozen-model", context.getRuntimeModelName());
            assertEquals(20, agent.getMaxSteps());
            assertEquals(ConfiguredReactAgentFactory.MAX_OBSERVE_CHARS, agent.getMaxObserve());
            assertEquals(true, agent.isFinishWithoutToolsAfterObservations());
            assertEquals(3, agent.getMaxToolObservationCount());
            assertEquals(true, agent.getSystemPrompt().contains("file_tool"));
            assertEquals(true, agent.getNextStepPrompt().contains("different authorized tool"));
            assertEquals(true, agent.getNextStepPrompt().contains("read_file followed by run_code"));
            assertEquals(true, agent.getNextStepPrompt().contains("Never repeat a tool with the same arguments"));
            assertEquals(true, agent.getNextStepPrompt().contains("Never paste html"));
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

    @Test
    void mapsHumanSkillEntrypointsToTheirExactRuntimeToolNames() {
        AgentRuntimeSkill skill = new AgentRuntimeSkill(
                "skill-json", 1L, 1, "Call inspect_json", null, "json-workbench",
                "FILESYSTEM", "1", "hash", List.of(),
                List.of(new SkillEntrypointView("inspect_json", SkillEntrypointRuntime.pyodide,
                        "scripts/entrypoint.py", "Inspect JSON", "{\"type\":\"object\"}"))
        );
        AgentRuntimeProfile mappedProfile = new AgentRuntimeProfile(
                "agent-1", 1L, "Agent", "description", "frozen prompt", "frozen-model",
                List.of(skill), List.of()
        );
        AgentContext context = AgentContext.builder().requestId("request-1").query("query")
                .task("task").dateInfo("").basePrompt("").build();
        context.setToolCollection(new ToolCollection());
        installApplicationContext(applicationContext());
        try {
            ReactImplAgent agent = factory.create(context, mappedProfile, new ConfiguredAgentPrinter(), 1);
            assertEquals(true, agent.getSystemPrompt().contains("Skill `json-workbench`, entrypoint `inspect_json`"));
            assertEquals(true, agent.getSystemPrompt().contains("runtime tool `skill_"));
            assertEquals(true, agent.getSystemPrompt().contains("never reuse a tool name from conversation history"));
        } finally {
            installApplicationContext(null);
        }
    }
}
