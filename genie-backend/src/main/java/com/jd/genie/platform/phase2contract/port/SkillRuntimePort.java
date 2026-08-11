package com.jd.genie.platform.phase2contract.port;

import com.jd.genie.agent.agent.AgentContext;
import com.jd.genie.agent.tool.BaseTool;
import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.phase2contract.dto.AgentRuntimeProfile;
import com.jd.genie.platform.phase2contract.dto.AgentSkillBindingSpec;
import com.jd.genie.platform.phase2contract.dto.SkillExecutionCommand;
import com.jd.genie.platform.phase2contract.dto.SkillExecutionResult;
import com.jd.genie.platform.phase2contract.dto.SkillResource;
import com.jd.genie.platform.phase2contract.dto.SkillRuntimePackage;

import java.util.List;

public interface SkillRuntimePort {

    List<SkillRuntimePackage> resolveForBindings(
        CurrentUser user,
        List<AgentSkillBindingSpec> bindings,
        boolean requireEnabled
    );

    List<SkillRuntimePackage> resolveForAgent(
        CurrentUser user,
        String agentId,
        boolean requireEnabled
    );

    SkillResource readResource(
        CurrentUser user,
        String skillId,
        String relativePath
    );

    List<BaseTool> buildRuntimeTools(
        CurrentUser user,
        AgentRuntimeProfile profile,
        AgentContext context
    );

    SkillExecutionResult executeEntrypoint(
        CurrentUser user,
        SkillExecutionCommand command
    );
}
