package com.jd.genie.platform.phase2.runtime.agent;

import com.jd.genie.agent.agent.AgentContext;
import com.jd.genie.agent.agent.ReactImplAgent;
import com.jd.genie.agent.printer.Printer;
import com.jd.genie.platform.agentbridge.AgentBridgeException;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2contract.dto.AgentRuntimeProfile;

public final class ConfiguredAgentExecutor {
    private static final int MAX_RAW_OUTPUT_LENGTH = 12_000;

    private final ConfiguredReactAgentFactory factory;
    private final AgentTaskResultParser parser;

    public ConfiguredAgentExecutor(ConfiguredReactAgentFactory factory, AgentTaskResultParser parser) {
        this.factory = factory;
        this.parser = parser;
    }

    public AgentTaskResult execute(
            AgentContext context,
            AgentRuntimeProfile profile,
            Printer printer,
            int maxSteps
    ) {
        try {
            ReactImplAgent agent = factory.create(context, profile, printer, maxSteps);
            String raw = agent.run(context.getQuery());
            return normalize(raw);
        } catch (AgentBridgeException error) {
            throw error;
        } catch (Exception error) {
            throw new AgentBridgeException(MvpErrorCode.INTERNAL_ERROR, "Configured agent execution failed", error);
        }
    }

    private AgentTaskResult normalize(String raw) {
        try {
            return parser.parse(raw);
        } catch (AgentBridgeException error) {
            if (error.getErrorCode() != MvpErrorCode.AGENT_INVALID_RESULT || raw == null || raw.isBlank()
                    || raw.length() > MAX_RAW_OUTPUT_LENGTH) {
                throw error;
            }
            String displayOutput = parser.extractDisplaySuccess(raw);
            return AgentTaskResult.success(displayOutput == null ? raw.trim() : displayOutput);
        }
    }
}
