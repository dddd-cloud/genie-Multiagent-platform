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
            
            You must finish by outputting ONLY one JSON object (no markdown fence, no extra keys):
            {"status":"SUCCESS","output":"<your final answer>","errorCode":null,"retryable":false}
            On failure use:
            {"status":"FAILURE","output":null,"errorCode":"EXECUTION_ERROR","retryable":true}
            Inside output, escape every double-quote as \\" and every newline as \\n.
            Keep output under 20000 characters: compact facts and sources, not a full search dump.
            When tools are available, you MUST call them for live/external facts (tickets, schedules, search, dates) instead of guessing or refusing.
            Prefer answering without tools only when the tool list is empty or the question needs no external data.
            For train/ticket tools: use trainFilterFlags=G for 高铁, set earliestStartTime/latestStartTime for the requested window, limitedNum=60 (never 0), sortFlag=startTime, format=text.
            Call get-tickets at most once. Prefer city/station Chinese names; skip station-code lookup unless required.
            After tools return a 票务查询摘要/schedule block: finish immediately with SUCCESS JSON.
            If the user asked for count + every departure time, put uniqueTrainCount and the full schedule list into output (one train per line: 车次 出发时间).
            Do NOT re-query for a fuller timetable. Do NOT invent times missing from schedule.
            If the user message describes a single step objective for you as one sub-agent, answer ONLY that objective.
            Do not discuss which other agents are available, and do not rewrite the whole multi-agent plan.
            If the user or your step asks for an html, markdown, or downloadable file, you MUST call file_tool with command=upload, a filename ending in .html or .md, description, and the full file content BEFORE the SUCCESS JSON. Put the returned file link into output.
            If a skill tool already returned token/previewFile/uploaded, do NOT paste html into output and do NOT call file_tool. Finish with SUCCESS whose output is one short sentence containing the token.
            """;

    /** Short nudge only — never re-inject the full system prompt (skills) each step. */
    private static final String NEXT_STEP_NUDGE = """
            After the latest tool result: finish NOW with ONLY the SUCCESS/FAILURE JSON object. Do not call another tool.
            If the tool JSON has token or previewFile, output one short sentence with that token. Never paste html or file content into output.
            Put compact bullets and sources into output (under 20000 characters). Do not paste the full search report.
            If a schedule block is present, answer from it (include all departures when asked). Do not call get-tickets again.
            """;
    static final int MAX_OBSERVE_CHARS = 2_000;

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
        agent.setNextStepPrompt(NEXT_STEP_NUDGE);
        agent.setNextStepPromptSnapshot(NEXT_STEP_NUDGE);
        agent.setLlm(new LLM(profile.resolvedModelName(), ""));
        agent.setMaxSteps(boundedSteps);
        agent.setMaxObserve(MAX_OBSERVE_CHARS);
        agent.setFinishWithoutToolsAfterObservations(true);
        agent.setLlmTimeoutSeconds(600);
        agent.setAvailableTools(context.getToolCollection());
        return agent;
    }
}
