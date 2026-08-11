package com.jd.genie.platform.phase2contract.port;

import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.phase2contract.dto.AgentSkillBindingSpec;
import com.jd.genie.platform.phase2contract.dto.AgentSkillBindingView;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface AgentSkillBindingPort {

    List<AgentSkillBindingView> loadForAgent(
        CurrentUser user,
        String agentId
    );

    @Transactional(propagation = Propagation.REQUIRED)
    void replaceForAgent(
        CurrentUser user,
        String agentId,
        List<AgentSkillBindingSpec> bindings
    );

    @Transactional(propagation = Propagation.REQUIRED)
    void removeForAgent(
        CurrentUser user,
        String agentId
    );
}
