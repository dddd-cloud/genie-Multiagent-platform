package com.jd.genie.platform.phase2contract.support;

import com.jd.genie.agent.agent.AgentContext;
import com.jd.genie.agent.tool.ToolCollection;
import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2contract.dto.AgentRuntimeProfile;
import com.jd.genie.platform.phase2contract.error.Phase2ContractException;
import com.jd.genie.platform.phase2contract.port.RuntimeToolCollectionPort;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class FakeRuntimeToolCollectionPort implements RuntimeToolCollectionPort {

    public record CallRecord(
        String userId,
        String agentId,
        String requestId,
        int capabilityKeyCount
    ) {
    }

    private final List<CallRecord> calls = new CopyOnWriteArrayList<>();
    private volatile ToolCollection toolCollection = new ToolCollection();
    private volatile RuntimeException buildException;

    public void setToolCollection(ToolCollection toolCollection) {
        if (toolCollection == null) {
            throw new Phase2ContractException(
                MvpErrorCode.VALIDATION_ERROR,
                "toolCollection must not be null"
            );
        }
        this.toolCollection = toolCollection;
    }

    public void setBuildException(RuntimeException exception) {
        this.buildException = exception;
    }

    public List<CallRecord> getCalls() {
        return Collections.unmodifiableList(new ArrayList<>(calls));
    }

    public void reset() {
        calls.clear();
        toolCollection = new ToolCollection();
        buildException = null;
    }

    @Override
    public ToolCollection build(
        CurrentUser user,
        AgentRuntimeProfile profile,
        AgentContext context
    ) {
        calls.add(new CallRecord(
            user == null ? null : user.userId(),
            profile == null ? null : profile.agentId(),
            context == null ? null : context.getRequestId(),
            profile == null || profile.capabilityKeys() == null ? -1 : profile.capabilityKeys().size()
        ));
        if (buildException != null) {
            throw buildException;
        }
        if (user == null || profile == null || context == null) {
            throw new Phase2ContractException(
                MvpErrorCode.VALIDATION_ERROR,
                "user, profile and context must not be null"
            );
        }
        ToolCollection result = toolCollection;
        if (result == null) {
            result = new ToolCollection();
            toolCollection = result;
        }
        return result;
    }
}
