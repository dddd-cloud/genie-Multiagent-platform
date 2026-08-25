package com.jd.genie.platform.phase2.runtime.agent;

import com.jd.genie.agent.agent.AgentContext;
import com.jd.genie.agent.agent.ReactImplAgent;
import com.jd.genie.agent.llm.LLM;
import com.jd.genie.agent.printer.Printer;
import com.jd.genie.agent.tool.ToolCollection;
import com.jd.genie.platform.phase2.runtime.context.BrowserWorkspaceContextPolicy;
import com.jd.genie.platform.phase2contract.dto.AgentRuntimeProfile;
import com.jd.genie.platform.phase2.skillruntime.packageinfo.SkillPackageHasher;

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
            When tools are available, call them for facts you cannot know reliably (live data, search, current dates)
            instead of guessing or refusing. Answer directly when the tool list is empty or the question needs no external data.
            Follow each tool's own description for its arguments and usage limits.
            If the user message describes a single step objective for you as one sub-agent, answer ONLY that objective.
            Do not discuss which other agents are available, and do not rewrite the whole multi-agent plan.
            If the user or your step asks for an html, markdown, or downloadable file, you MUST call file_tool with command=upload, a filename ending in .html or .md, description, and the full file content BEFORE the SUCCESS JSON. Put the returned file link into output.
            If a skill tool already returned token/previewFile/uploaded, do NOT paste html into output and do NOT call file_tool. Finish with SUCCESS whose output is one short sentence containing the token.
            """;

    /** Appended only when this request carries a bound browser-workspace snapshot — see {@link BrowserWorkspaceContextPolicy}. */
    private static final String BROWSER_WORKSPACE_INSTRUCTIONS = """

            The browser workspace context contains only a lightweight file index and summaries, never full file bodies.
            Use browser_workspace_python command=read_file only when the answer truly needs one selected file's content;
            do not read unrelated files. Use list_files only when the supplied index is truncated or insufficient.
            Use run_script/run_code only for actual Python execution, computation, transformation, or workspace changes.
            To run an existing workspace script, call browser_workspace_python with command=run_script and its exact
            /workspace/<filename>.py path. Use command=run_code only for new inline code. Never translate /workspace
            paths into input/ or output/ paths; changed and newly created workspace files are synchronized automatically.
            """;

    /** Short nudge only — never re-inject the full system prompt (skills) each step. */
    private static final String NEXT_STEP_NUDGE = """
            After the latest tool result, check whether the current step objective is complete.
            If it is complete, finish NOW with ONLY the SUCCESS/FAILURE JSON object.
            If it is still incomplete and the existing result supplies inputs required by a different authorized tool or command,
            call only that necessary next tool. A workspace task may require read_file followed by run_code or an exact
            mapped Skill tool, and then a file/chart tool.
            Never repeat a tool with the same arguments and never make exploratory calls unrelated to the step objective.
            If the tool JSON has token or previewFile, output one short sentence with that token. Never paste html or file content into output.
            Put compact bullets and sources into output (under 20000 characters). Do not paste the full search report.
            Answer from the data the tool already returned; do not repeat the same query for a fuller result.
            """;
    static final int MAX_OBSERVE_CHARS = 2_000;
    private final SkillPackageHasher packageHasher = new SkillPackageHasher();

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
                + skillToolMapping(profile)
                + RESULT_CONTRACT
                + (BrowserWorkspaceContextPolicy.hasSnapshot(context.getQuery()) ? BROWSER_WORKSPACE_INSTRUCTIONS : "");
        agent.setSystemPrompt(prompt);
        agent.setSystemPromptSnapshot(prompt);
        agent.setNextStepPrompt(NEXT_STEP_NUDGE);
        agent.setNextStepPromptSnapshot(NEXT_STEP_NUDGE);
        LLM llm = new LLM(profile.resolvedModelName(), "");
        // Tool instances hold this same context, so they observe the actual model resolved for this request.
        context.setRuntimeModelName(llm.getModel());
        agent.setLlm(llm);
        agent.setMaxSteps(boundedSteps);
        agent.setMaxObserve(MAX_OBSERVE_CHARS);
        // Permit a short dependency chain (for example read CSV -> analyze -> chart),
        // then force a final response so tool loops remain bounded.
        agent.setMaxToolObservationCount(3);
        agent.setFinishWithoutToolsAfterObservations(true);
        agent.setLlmTimeoutSeconds(600);
        agent.setAvailableTools(context.getToolCollection());
        return agent;
    }

    private String skillToolMapping(AgentRuntimeProfile profile) {
        StringBuilder mapping = new StringBuilder("\n# Exact Skill Tool Mapping\n\n");
        boolean present = false;
        for (var skill : profile.skills()) {
            for (var entrypoint : skill.entrypoints()) {
                present = true;
                mapping.append("- Skill `")
                        .append(skill.skillKey() == null || skill.skillKey().isBlank() ? skill.skillId() : skill.skillKey())
                        .append("`, entrypoint `").append(entrypoint.name())
                        .append("`: call runtime tool `")
                        .append(packageHasher.runtimeToolName(skill.skillId(), entrypoint.name()))
                        .append("`\n");
            }
        }
        if (!present) {
            return "";
        }
        mapping.append("Use only the exact runtime tool mapped to the requested Skill entrypoint; never reuse a tool name from conversation history.\n");
        return mapping.toString();
    }
}
