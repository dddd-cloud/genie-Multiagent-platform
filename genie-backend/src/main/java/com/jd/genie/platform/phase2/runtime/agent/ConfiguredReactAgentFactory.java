package com.jd.genie.platform.phase2.runtime.agent;

import com.jd.genie.agent.agent.AgentContext;
import com.jd.genie.agent.agent.ReactImplAgent;
import com.jd.genie.agent.llm.LLM;
import com.jd.genie.agent.printer.Printer;
import com.jd.genie.agent.tool.ToolCollection;
import com.jd.genie.platform.phase2contract.dto.AgentRuntimeProfile;

public final class ConfiguredReactAgentFactory {
    private static final int DEFAULT_MAX_STEPS = 10;
    private static final int MAX_MAX_STEPS = 20;

    public ReactImplAgent create(
            AgentContext context,
            AgentRuntimeProfile profile,
            Printer printer,
            int maxSteps
    ) {
        int boundedSteps = Math.max(1, Math.min(MAX_MAX_STEPS, maxSteps <= 0 ? DEFAULT_MAX_STEPS : maxSteps));
        context.setPrinter(printer);
        ReactImplAgent agent = new ReactImplAgent(context);
        agent.setSystemPrompt(profile.compiledSystemPromptTemplate());
        agent.setSystemPromptSnapshot(profile.compiledSystemPromptTemplate());
        agent.setNextStepPrompt(profile.compiledSystemPromptTemplate());
        agent.setNextStepPromptSnapshot(profile.compiledSystemPromptTemplate());
        agent.setLlm(new LLM(profile.resolvedModelName(), ""));
        agent.setMaxSteps(boundedSteps);
        agent.setAvailableTools(context.getToolCollection() == null ? new ToolCollection() : context.getToolCollection());
        return agent;
    }
}
