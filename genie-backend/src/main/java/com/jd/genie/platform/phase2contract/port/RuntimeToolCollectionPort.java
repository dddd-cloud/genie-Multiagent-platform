package com.jd.genie.platform.phase2contract.port;

import com.jd.genie.agent.agent.AgentContext;
import com.jd.genie.agent.tool.ToolCollection;
import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.phase2contract.dto.AgentRuntimeProfile;

public interface RuntimeToolCollectionPort {

    ToolCollection build(
        CurrentUser user,
        AgentRuntimeProfile profile,
        AgentContext context
    );
}
