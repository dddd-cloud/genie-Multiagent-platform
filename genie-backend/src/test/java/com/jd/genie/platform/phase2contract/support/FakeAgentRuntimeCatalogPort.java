package com.jd.genie.platform.phase2contract.support;

import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2contract.dto.AgentCapabilitySummary;
import com.jd.genie.platform.phase2contract.dto.AgentRuntimeProfile;
import com.jd.genie.platform.phase2contract.error.Phase2ContractException;
import com.jd.genie.platform.phase2contract.port.AgentRuntimeCatalogPort;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class FakeAgentRuntimeCatalogPort implements AgentRuntimeCatalogPort {

    public enum CallType {
        LIST_ONLINE_CANDIDATES,
        LOAD_ONLINE_PROFILE
    }

    public record CallRecord(
        CallType type,
        String userId,
        List<String> allowedAgentIds,
        String agentId
    ) {
    }

    private final List<CallRecord> calls = new CopyOnWriteArrayList<>();
    private final Map<String, AgentCapabilitySummary> summaries = new ConcurrentHashMap<>();
    private final Map<String, AgentRuntimeProfile> profiles = new ConcurrentHashMap<>();
    private final Map<String, MvpErrorCode> loadFailures = new ConcurrentHashMap<>();
    private volatile RuntimeException listException;
    private volatile RuntimeException loadException;

    public void registerSummary(AgentCapabilitySummary summary) {
        if (summary == null) {
            throw new Phase2ContractException(
                MvpErrorCode.VALIDATION_ERROR,
                "summary must not be null"
            );
        }
        requireAgentId(summary.agentId());
        summaries.put(summary.agentId(), summary);
    }

    public void registerProfile(AgentRuntimeProfile profile) {
        if (profile == null) {
            throw new Phase2ContractException(
                MvpErrorCode.VALIDATION_ERROR,
                "profile must not be null"
            );
        }
        requireAgentId(profile.agentId());
        profiles.put(profile.agentId(), profile);
    }

    /**
     * Configures the deterministic visibility/state result used by C tests.
     * RESOURCE_NOT_FOUND models deleted/foreign resources without exposing which
     * case occurred; AGENT_OFFLINE models a visible agent that is not runnable.
     */
    public void failLoad(String agentId, MvpErrorCode errorCode) {
        requireAgentId(agentId);
        if (errorCode != MvpErrorCode.RESOURCE_NOT_FOUND
            && errorCode != MvpErrorCode.AGENT_OFFLINE) {
            throw new Phase2ContractException(
                MvpErrorCode.VALIDATION_ERROR,
                "load failure must be RESOURCE_NOT_FOUND or AGENT_OFFLINE"
            );
        }
        loadFailures.put(agentId, errorCode);
    }

    public void markOffline(String agentId) {
        failLoad(agentId, MvpErrorCode.AGENT_OFFLINE);
        summaries.remove(agentId);
    }

    public void hideFromUser(String agentId) {
        failLoad(agentId, MvpErrorCode.RESOURCE_NOT_FOUND);
        summaries.remove(agentId);
    }

    public void clearLoadFailure(String agentId) {
        requireAgentId(agentId);
        loadFailures.remove(agentId);
    }

    public void setListException(RuntimeException exception) {
        this.listException = exception;
    }

    public void setLoadException(RuntimeException exception) {
        this.loadException = exception;
    }

    public List<CallRecord> getCalls() {
        return Collections.unmodifiableList(new ArrayList<>(calls));
    }

    public void reset() {
        calls.clear();
        summaries.clear();
        profiles.clear();
        loadFailures.clear();
        listException = null;
        loadException = null;
    }

    @Override
    public List<AgentCapabilitySummary> listOnlineCandidates(
        CurrentUser user,
        List<String> allowedAgentIds
    ) {
        calls.add(new CallRecord(
            CallType.LIST_ONLINE_CANDIDATES,
            user == null ? null : user.userId(),
            allowedAgentIds == null ? null : List.copyOf(allowedAgentIds),
            null
        ));
        if (listException != null) {
            throw listException;
        }
        if (user == null) {
            throw new Phase2ContractException(
                MvpErrorCode.VALIDATION_ERROR,
                "user must not be null"
            );
        }
        if (allowedAgentIds == null) {
            throw new Phase2ContractException(
                MvpErrorCode.VALIDATION_ERROR,
                "allowedAgentIds must not be null"
            );
        }
        List<AgentCapabilitySummary> result = new ArrayList<>();
        if (allowedAgentIds.isEmpty()) {
            result.addAll(summaries.values());
            result.sort(Comparator.comparing(AgentCapabilitySummary::agentId));
        } else {
            for (String agentId : allowedAgentIds) {
                AgentCapabilitySummary summary = summaries.get(agentId);
                if (summary != null) {
                    result.add(summary);
                }
            }
        }
        return List.copyOf(result);
    }

    @Override
    public AgentRuntimeProfile loadOnlineProfile(
        CurrentUser user,
        String agentId
    ) {
        calls.add(new CallRecord(
            CallType.LOAD_ONLINE_PROFILE,
            user == null ? null : user.userId(),
            null,
            agentId
        ));
        if (loadException != null) {
            throw loadException;
        }
        if (user == null) {
            throw new Phase2ContractException(
                MvpErrorCode.VALIDATION_ERROR,
                "user must not be null"
            );
        }
        if (agentId == null || agentId.isBlank()) {
            throw new Phase2ContractException(
                MvpErrorCode.VALIDATION_ERROR,
                "agentId must not be blank"
            );
        }
        MvpErrorCode configuredFailure = loadFailures.get(agentId);
        if (configuredFailure != null) {
            throw new Phase2ContractException(
                configuredFailure,
                configuredFailure == MvpErrorCode.AGENT_OFFLINE
                    ? "agent is offline"
                    : "agent profile not found"
            );
        }
        AgentRuntimeProfile profile = profiles.get(agentId);
        if (profile == null) {
            throw new Phase2ContractException(
                MvpErrorCode.RESOURCE_NOT_FOUND,
                "agent profile not found"
            );
        }
        return profile;
    }

    public Map<String, AgentCapabilitySummary> registeredSummaries() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(summaries));
    }

    private static void requireAgentId(String agentId) {
        if (agentId == null || agentId.isBlank()) {
            throw new Phase2ContractException(
                MvpErrorCode.VALIDATION_ERROR,
                "agentId must not be blank"
            );
        }
    }
}
