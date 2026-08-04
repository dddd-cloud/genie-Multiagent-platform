package com.jd.genie.platform.phase2contract.support;

import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2contract.capability.CapabilityKeys;
import com.jd.genie.platform.phase2contract.dto.ToolBindingView;
import com.jd.genie.platform.phase2contract.error.Phase2ContractException;
import com.jd.genie.platform.phase2contract.port.ToolBindingPort;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
    private final Map<String, List<String>> agentBindings = new ConcurrentHashMap<>();
    private final Map<String, List<String>> skillBindings = new ConcurrentHashMap<>();
    private volatile ToolBindingView resolveOverride;
    private volatile RuntimeException resolveException;
    private volatile RuntimeException writeException;

    public void setResolveResult(ToolBindingView result) {
        this.resolveOverride = result;
    }

    public void clearResolveResult() {
        this.resolveOverride = null;
    }

    public void setResolveException(RuntimeException exception) {
        this.resolveException = exception;
    }

    public void setWriteException(RuntimeException exception) {
        this.writeException = exception;
    }

    public void failCapabilityKey(String capabilityKey) {
        CapabilityKeys.requireValid(capabilityKey);
        failingCapabilityKeys.add(capabilityKey);
    }

    public List<String> getAgentBindings(CurrentUser user, String agentId) {
        requireUser(user);
        requireResourceId(agentId);
        return agentBindings.getOrDefault(scopedKey(user, agentId), List.of());
    }

    public List<String> getSkillBindings(CurrentUser user, String skillId) {
        requireUser(user);
        requireResourceId(skillId);
        return skillBindings.getOrDefault(scopedKey(user, skillId), List.of());
    }

    public List<CallRecord> getCalls() {
        return Collections.unmodifiableList(new ArrayList<>(calls));
    }

    public void reset() {
        calls.clear();
        failingCapabilityKeys.clear();
        agentBindings.clear();
        skillBindings.clear();
        resolveOverride = null;
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
        requireUser(user);
        requireResourceId(agentId);
        if (enabledSkillIds == null) {
            throw new Phase2ContractException(
                MvpErrorCode.VALIDATION_ERROR,
                "enabledSkillIds must not be null"
            );
        }
        ToolBindingView override = resolveOverride;
        if (override != null) {
            return override;
        }

        List<String> invalid = new ArrayList<>();
        List<String> direct = partitionValid(
            agentBindings.getOrDefault(scopedKey(user, agentId), List.of()),
            invalid
        );
        Map<String, List<String>> skills = new LinkedHashMap<>();
        for (String skillId : enabledSkillIds) {
            requireResourceId(skillId);
            List<String> configured = skillBindings.getOrDefault(scopedKey(user, skillId), List.of());
            skills.put(skillId, partitionValid(configured, invalid));
        }
        return new ToolBindingView(direct, skills, List.copyOf(new LinkedHashSet<>(invalid)));
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
        remove(CallType.REMOVE_AGENT_BINDINGS, user, agentId);
    }

    @Override
    public void removeSkillBindings(CurrentUser user, String skillId) {
        remove(CallType.REMOVE_SKILL_BINDINGS, user, skillId);
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
        requireUser(user);
        requireResourceId(resourceId);
        if (capabilityKeys == null) {
            throw new Phase2ContractException(
                MvpErrorCode.VALIDATION_ERROR,
                "capabilityKeys must not be null"
            );
        }
        CapabilityKeys.requireAllValid(capabilityKeys);
        for (String key : capabilityKeys) {
            if (failingCapabilityKeys.contains(key)) {
                throw new Phase2ContractException(
                    MvpErrorCode.TOOL_BINDING_INVALID,
                    "capabilityKeys contains an invalid entry"
                );
            }
        }
        Map<String, List<String>> target = type == CallType.REPLACE_AGENT_BINDINGS
            ? agentBindings
            : skillBindings;
        String key = scopedKey(user, resourceId);
        if (capabilityKeys.isEmpty()) {
            target.remove(key);
        } else {
            target.put(key, List.copyOf(capabilityKeys));
        }
    }

    private void remove(CallType type, CurrentUser user, String resourceId) {
        calls.add(new CallRecord(
            type,
            user == null ? null : user.userId(),
            resourceId,
            null,
            null
        ));
        if (writeException != null) {
            throw writeException;
        }
        requireUser(user);
        requireResourceId(resourceId);
        Map<String, List<String>> target = type == CallType.REMOVE_AGENT_BINDINGS
            ? agentBindings
            : skillBindings;
        target.remove(scopedKey(user, resourceId));
    }

    private static void requireUser(CurrentUser user) {
        if (user == null) {
            throw new Phase2ContractException(
                MvpErrorCode.VALIDATION_ERROR,
                "user must not be null"
            );
        }
    }

    private static void requireResourceId(String resourceId) {
        if (resourceId == null || resourceId.isBlank()) {
            throw new Phase2ContractException(
                MvpErrorCode.VALIDATION_ERROR,
                "resource id must not be blank"
            );
        }
    }

    private List<String> partitionValid(List<String> configured, List<String> invalid) {
        List<String> valid = new ArrayList<>();
        for (String capabilityKey : configured) {
            if (failingCapabilityKeys.contains(capabilityKey)) {
                invalid.add(capabilityKey);
            } else {
                valid.add(capabilityKey);
            }
        }
        return List.copyOf(valid);
    }

    private static String scopedKey(CurrentUser user, String resourceId) {
        return user.tenantId() + "\u0000" + user.userId() + "\u0000" + resourceId;
    }
}
