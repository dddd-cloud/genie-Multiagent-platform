package com.jd.genie.platform.phase2contract.support;

import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2contract.dto.AgentSkillBindingSpec;
import com.jd.genie.platform.phase2contract.dto.AgentSkillBindingView;
import com.jd.genie.platform.phase2contract.error.Phase2ContractException;
import com.jd.genie.platform.phase2contract.port.AgentSkillBindingPort;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class FakeAgentSkillBindingPort implements AgentSkillBindingPort {

    public enum CallType {
        LOAD_FOR_AGENT,
        REPLACE_FOR_AGENT,
        REMOVE_FOR_AGENT
    }

    public record CallRecord(
        CallType type,
        String userId,
        String agentId,
        List<AgentSkillBindingSpec> bindings
    ) {
    }

    private final List<CallRecord> calls = new CopyOnWriteArrayList<>();
    private final Map<String, List<AgentSkillBindingView>> bindingsByAgent = new ConcurrentHashMap<>();
    private volatile RuntimeException loadException;
    private volatile RuntimeException writeException;

    public void setLoadException(RuntimeException exception) {
        this.loadException = exception;
    }

    public void setWriteException(RuntimeException exception) {
        this.writeException = exception;
    }

    public void seed(CurrentUser user, String agentId, List<AgentSkillBindingView> bindings) {
        requireUser(user);
        requireAgentId(agentId);
        String key = scopedKey(user, agentId);
        if (bindings == null || bindings.isEmpty()) {
            bindingsByAgent.remove(key);
        } else {
            bindingsByAgent.put(key, List.copyOf(bindings));
        }
    }

    public List<AgentSkillBindingView> getBindings(CurrentUser user, String agentId) {
        requireUser(user);
        requireAgentId(agentId);
        return bindingsByAgent.getOrDefault(scopedKey(user, agentId), List.of());
    }

    public List<CallRecord> getCalls() {
        return Collections.unmodifiableList(new ArrayList<>(calls));
    }

    public void reset() {
        calls.clear();
        bindingsByAgent.clear();
        loadException = null;
        writeException = null;
    }

    @Override
    public List<AgentSkillBindingView> loadForAgent(CurrentUser user, String agentId) {
        calls.add(new CallRecord(
            CallType.LOAD_FOR_AGENT,
            user == null ? null : user.userId(),
            agentId,
            null
        ));
        if (loadException != null) {
            throw loadException;
        }
        requireUser(user);
        requireAgentId(agentId);
        return bindingsByAgent.getOrDefault(scopedKey(user, agentId), List.of());
    }

    @Override
    public void replaceForAgent(CurrentUser user, String agentId, List<AgentSkillBindingSpec> bindings) {
        calls.add(new CallRecord(
            CallType.REPLACE_FOR_AGENT,
            user == null ? null : user.userId(),
            agentId,
            bindings == null ? null : List.copyOf(bindings)
        ));
        if (writeException != null) {
            throw writeException;
        }
        requireUser(user);
        requireAgentId(agentId);
        List<AgentSkillBindingSpec> normalized = bindings == null ? List.of() : List.copyOf(bindings);
        String key = scopedKey(user, agentId);
        if (normalized.isEmpty()) {
            bindingsByAgent.remove(key);
            return;
        }
        List<AgentSkillBindingView> views = normalized.stream()
            .sorted(Comparator.comparingInt(AgentSkillBindingSpec::sortOrder))
            .map(spec -> {
                if (spec.skillId() == null || spec.skillId().isBlank()) {
                    throw new Phase2ContractException(
                        MvpErrorCode.VALIDATION_ERROR,
                        "skillId must not be blank"
                    );
                }
                return new AgentSkillBindingView(spec.skillId(), spec.sortOrder());
            })
            .toList();
        bindingsByAgent.put(key, views);
    }

    @Override
    public void removeForAgent(CurrentUser user, String agentId) {
        calls.add(new CallRecord(
            CallType.REMOVE_FOR_AGENT,
            user == null ? null : user.userId(),
            agentId,
            null
        ));
        if (writeException != null) {
            throw writeException;
        }
        requireUser(user);
        requireAgentId(agentId);
        bindingsByAgent.remove(scopedKey(user, agentId));
    }

    private static void requireUser(CurrentUser user) {
        if (user == null) {
            throw new Phase2ContractException(
                MvpErrorCode.VALIDATION_ERROR,
                "user must not be null"
            );
        }
    }

    private static void requireAgentId(String agentId) {
        if (agentId == null || agentId.isBlank()) {
            throw new Phase2ContractException(
                MvpErrorCode.VALIDATION_ERROR,
                "agentId must not be blank"
            );
        }
    }

    private static String scopedKey(CurrentUser user, String agentId) {
        return user.tenantId() + "\u0000" + user.userId() + "\u0000" + agentId.trim();
    }
}
