package com.jd.genie.platform.phase2.runtime.agent;

import com.jd.genie.agent.agent.AgentContext;
import com.jd.genie.agent.dto.File;
import com.jd.genie.agent.tool.ToolCollection;
import com.jd.genie.agent.util.DateUtil;
import com.jd.genie.platform.agentbridge.AgentBridgeException;
import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.CurrentUserProvider;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2contract.dto.AgentRuntimeProfile;
import com.jd.genie.platform.phase2contract.dto.AgentRuntimeSkill;
import com.jd.genie.platform.phase2contract.error.Phase2ContractException;
import com.jd.genie.platform.phase2contract.port.AgentRuntimeCatalogPort;
import com.jd.genie.platform.phase2contract.port.RuntimeToolCollectionPort;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class AgentTestService {
    private static final int MAX_AGENT_STEPS = 10;
    private static final int MAX_QUERY_CODE_POINTS = 20_000;

    private final CurrentUserProvider currentUserProvider;
    private final AgentRuntimeCatalogPort catalogPort;
    private final RuntimeToolCollectionPort toolCollectionPort;
    private final ConfiguredAgentExecutor executor;

    public AgentTestService(
            CurrentUserProvider currentUserProvider,
            AgentRuntimeCatalogPort catalogPort,
            RuntimeToolCollectionPort toolCollectionPort,
            ConfiguredAgentExecutor executor
    ) {
        this.currentUserProvider = currentUserProvider;
        this.catalogPort = catalogPort;
        this.toolCollectionPort = toolCollectionPort;
        this.executor = executor;
    }

    public AgentTestResponse test(String agentId, AgentTestRequest request) {
        validate(agentId, request);
        try {
            CurrentUser user = currentUserProvider.requireCurrentUser();
            AgentRuntimeProfile profile = catalogPort.loadOnlineProfile(user, agentId);
            List<File> emptyFiles = new ArrayList<>();
            AgentContext context = AgentContext.builder()
                    .requestId(UUID.randomUUID().toString())
                    .sessionId("agent-test")
                    .query(request.query())
                    .task(request.query())
                    .basePrompt(request.query())
                    .dateInfo(DateUtil.CurrentDateInfo())
                    .productFiles(emptyFiles)
                    .taskProductFiles(emptyFiles)
                    .isStream(false)
                    .templateType("empty")
                    .build();
            ToolCollection tools = toolCollectionPort.build(user, profile, context);
            context.setToolCollection(tools);

            ConfiguredAgentPrinter printer = new ConfiguredAgentPrinter();
            long startedAt = System.nanoTime();
            try {
                AgentTaskResult result = executor.execute(context, profile, printer, MAX_AGENT_STEPS);
                return new AgentTestResponse(
                        profile.resolvedModelName(),
                        safeSkillSummary(profile.skills()),
                        profile.capabilityKeys(),
                        result,
                        elapsedMillis(startedAt),
                        printer.progressCount()
                );
            } finally {
                printer.close();
            }
        } catch (Phase2ContractException error) {
            throw new AgentBridgeException(error.errorCode(), "Agent test request cannot be completed", error);
        }
    }

    private void validate(String agentId, AgentTestRequest request) {
        if (agentId == null || agentId.isBlank() || request == null || request.query() == null || request.query().isBlank()
                || request.query().codePointCount(0, request.query().length()) > MAX_QUERY_CODE_POINTS) {
            throw new AgentBridgeException(MvpErrorCode.VALIDATION_ERROR, "Invalid agent test request");
        }
    }

    private List<String> safeSkillSummary(List<AgentRuntimeSkill> skills) {
        return skills.stream()
                .map(AgentRuntimeSkill::skillId)
                .filter(skillId -> skillId != null && !skillId.isBlank())
                .toList();
    }

    private long elapsedMillis(long startedAt) {
        return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000L);
    }
}
