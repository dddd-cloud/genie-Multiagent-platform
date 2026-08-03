package com.jd.genie.platform.phase2contract.port;

import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.phase2contract.dto.ToolBindingView;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface ToolBindingPort {

    ToolBindingView resolveBindings(
        CurrentUser user,
        String agentId,
        List<String> enabledSkillIds
    );

    @Transactional(propagation = Propagation.REQUIRED)
    void replaceAgentBindings(
        CurrentUser user,
        String agentId,
        List<String> capabilityKeys
    );

    @Transactional(propagation = Propagation.REQUIRED)
    void replaceSkillBindings(
        CurrentUser user,
        String skillId,
        List<String> capabilityKeys
    );

    @Transactional(propagation = Propagation.REQUIRED)
    void removeAgentBindings(
        CurrentUser user,
        String agentId
    );

    @Transactional(propagation = Propagation.REQUIRED)
    void removeSkillBindings(
        CurrentUser user,
        String skillId
    );
}
