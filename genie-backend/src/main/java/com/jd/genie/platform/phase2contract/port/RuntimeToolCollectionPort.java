package com.jd.genie.platform.phase2contract.port;

import com.jd.genie.agent.agent.AgentContext;
import com.jd.genie.agent.tool.BaseTool;
import com.jd.genie.agent.tool.ToolCollection;
import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.phase2contract.dto.AgentRuntimeProfile;

import java.util.List;

public interface RuntimeToolCollectionPort {

    default ToolCollection build(
        CurrentUser user,
        AgentRuntimeProfile profile,
        AgentContext context
    ) {
        return build(user, profile, context, List.of());
    }

    ToolCollection build(
        CurrentUser user,
        AgentRuntimeProfile profile,
        AgentContext context,
        List<BaseTool> additionalTools
    );
}
