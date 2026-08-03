package com.jd.genie.platform.phase2contract.support;

import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2contract.dto.ToolBindingView;
import com.jd.genie.platform.phase2contract.error.Phase2ContractException;
import com.jd.genie.platform.phase2contract.port.ToolBindingPort;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class FakeToolBindingPort implements ToolBindingPort {

    public enum CallType {
        RESOLVE_BINDINGS,
        REPLACE_AGENT_BINDINGS,
        REPLACE_SKILL_BINDINGS,
        REMOVE_AGENT_BINDINGS,
        REMOVE_SKILL_BINDINGS
    }

    public record CallRecord(
        CallType type,
        String userId,
        String resourceId,
        List<String> capabilityKeys,
        List<String> enabledSkillIds
    ) {
    }

    private final List<CallRecord> calls = new CopyOnWriteArrayList<>();
    private final Set<String> failingCapabilityKeys = ConcurrentHashMap.newKeySet();
    private volatile ToolBindingView resolveResult = new ToolBindingView(List.of(), Map.of(), List.of());
    private volatile RuntimeException resolveException;
    private volatile RuntimeException writeException;

    public void setResolveResult(ToolBindingView result) {
        this.resolveResult = result == null
            ? new ToolBindingView(List.of(), Map.of(), List.of())
            : result;
    }

    public void setResolveException(RuntimeException exception) {
        this.resolveException = exception;
    }

    public void setWriteException(RuntimeException exception) {
        this.writeException = exception;
    }

    public void failCapabilityKey(String capabilityKey) {
        failingCapabilityKeys.add(capabilityKey);
    }

    public List<CallRecord> getCalls() {
        return Collections.unmodifiableList(new ArrayList<>(calls));
    }

    public void reset() {
        calls.clear();
        failingCapabilityKeys.clear();
        resolveResult = new ToolBindingView(List.of(), Map.of(), List.of());
        resolveException = null;
        writeException = null;
    }

    @Override
    public ToolBindingView resolveBindings(
        CurrentUser user,
        String agentId,
        List<String> enabledSkillIds
    ) {
        calls.add(new CallRecord(
            CallType.RESOLVE_BINDINGS,
            user == null ? null : user.userId(),
            agentId,
            null,
            enabledSkillIds == null ? null : List.copyOf(enabledSkillIds)
        ));
        if (resolveException != null) {
            throw resolveException;
        }
        if (enabledSkillIds == null) {
            throw new Phase2ContractException(
                MvpErrorCode.VALIDATION_ERROR,
                "enabledSkillIds must not be null"
            );
        }
        return resolveResult;
    }

    @Override
    public void replaceAgentBindings(
        CurrentUser user,
        String agentId,
        List<String> capabilityKeys
    ) {
        replace(CallType.REPLACE_AGENT_BINDINGS, user, agentId, capabilityKeys);
    }

    @Override
    public void replaceSkillBindings(
        CurrentUser user,
        String skillId,
        List<String> capabilityKeys
    ) {
        replace(CallType.REPLACE_SKILL_BINDINGS, user, skillId, capabilityKeys);
    }

    @Override
    public void removeAgentBindings(CurrentUser user, String agentId) {
        calls.add(new CallRecord(
            CallType.REMOVE_AGENT_BINDINGS,
            user == null ? null : user.userId(),
            agentId,
            null,
            null
        ));
        if (writeException != null) {
            throw writeException;
        }
    }

    @Override
    public void removeSkillBindings(CurrentUser user, String skillId) {
        calls.add(new CallRecord(
            CallType.REMOVE_SKILL_BINDINGS,
            user == null ? null : user.userId(),
            skillId,
            null,
            null
        ));
        if (writeException != null) {
            throw writeException;
        }
    }

    private void replace(
        CallType type,
        CurrentUser user,
        String resourceId,
        List<String> capabilityKeys
    ) {
        calls.add(new CallRecord(
            type,
            user == null ? null : user.userId(),
            resourceId,
            capabilityKeys == null ? null : List.copyOf(capabilityKeys),
            null
        ));
        if (writeException != null) {
            throw writeException;
        }
        if (capabilityKeys == null) {
            throw new Phase2ContractException(
                MvpErrorCode.VALIDATION_ERROR,
                "capabilityKeys must not be null"
            );
        }
        Set<String> unique = new HashSet<>();
        for (String key : capabilityKeys) {
            if (key == null || key.isBlank() || !unique.add(key) || failingCapabilityKeys.contains(key)) {
                throw new Phase2ContractException(
                    MvpErrorCode.TOOL_BINDING_INVALID,
                    "capabilityKeys contains an invalid entry"
                );
            }
        }
        // Empty list means clear all bindings — success with no further action.
    }
}
