package com.jd.genie.platform.phase2contract.port;

import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.phase2contract.dto.AgentCapabilitySummary;
import com.jd.genie.platform.phase2contract.dto.AgentRuntimeProfile;

import java.util.List;

public interface AgentRuntimeCatalogPort {

    List<AgentCapabilitySummary> listOnlineCandidates(
        CurrentUser user,
        List<String> allowedAgentIds
    );

    AgentRuntimeProfile loadOnlineProfile(
        CurrentUser user,
        String agentId
    );
}
