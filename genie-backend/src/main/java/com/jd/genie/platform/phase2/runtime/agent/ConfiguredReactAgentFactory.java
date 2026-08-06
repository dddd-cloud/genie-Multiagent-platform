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
    /**
     * Frozen result contract for Phase2 configured agents (C runtime).
     * Must remain a single JSON object — never wrap success outside the parser.
     */
    private static final String RESULT_CONTRACT = """
            
            You must finish by outputting ONLY one JSON object (no markdown, no extra text):
            {"status":"SUCCESS","output":"<your final answer>","errorCode":null,"retryable":false}
            On failure use:
            {"status":"FAILURE","output":null,"errorCode":"EXECUTION_ERROR","retryable":true}
            Prefer answering without tools when tools are empty or unnecessary.
            """;

    public ReactImplAgent create(
            AgentContext context,
            AgentRuntimeProfile profile,
            Printer printer,
            int maxSteps
    ) {
        int boundedSteps = Math.max(1, Math.min(MAX_MAX_STEPS, maxSteps <= 0 ? DEFAULT_MAX_STEPS : maxSteps));
        context.setPrinter(printer);
        if (context.getDateInfo() == null) {
            context.setDateInfo("");
        }
        if (context.getBasePrompt() == null) {
            context.setBasePrompt("");
        }
        if (context.getQuery() == null) {
            context.setQuery("");
        }
        if (context.getProductFiles() == null) {
            context.setProductFiles(java.util.List.of());
        }
        if (context.getToolCollection() == null) {
            context.setToolCollection(new ToolCollection());
        }
        ReactImplAgent agent = new ReactImplAgent(context);
        String prompt = (profile.compiledSystemPromptTemplate() == null ? "" : profile.compiledSystemPromptTemplate())
                + RESULT_CONTRACT;
        agent.setSystemPrompt(prompt);
        agent.setSystemPromptSnapshot(prompt);
        agent.setNextStepPrompt(prompt);
        agent.setNextStepPromptSnapshot(prompt);
        agent.setLlm(new LLM(profile.resolvedModelName(), ""));
        agent.setMaxSteps(boundedSteps);
        agent.setAvailableTools(context.getToolCollection());
        return agent;
    }
}
