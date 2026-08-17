package com.jd.genie.platform.phase2.configuration.team.runtime;

import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2.configuration.team.entity.AgentTeamEntity;
import com.jd.genie.platform.phase2.configuration.team.entity.AgentTeamMemberEntity;
import com.jd.genie.platform.phase2.configuration.team.mapper.AgentTeamMapper;
import com.jd.genie.platform.phase2.configuration.team.mapper.AgentTeamMemberMapper;
import com.jd.genie.platform.phase2contract.dto.AgentCapabilitySummary;
import com.jd.genie.platform.phase2contract.dto.AgentRuntimeProfile;
import com.jd.genie.platform.phase2contract.dto.MasterPersona;
import com.jd.genie.platform.phase2contract.dto.TeamRuntimeSelection;
import com.jd.genie.platform.phase2contract.error.Phase2ContractException;
import com.jd.genie.platform.phase2contract.port.AgentRuntimeCatalogPort;
import com.jd.genie.platform.phase2contract.port.TeamRuntimeCatalogPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TeamRuntimeResolver implements TeamRuntimeCatalogPort {

    private final AgentTeamMapper teamMapper;
    private final AgentTeamMemberMapper memberMapper;
    private final AgentRuntimeCatalogPort agentRuntimeCatalogPort;

    @Override
    @Transactional(readOnly = true)
    public TeamRuntimeSelection resolve(CurrentUser user, String teamId) {
        requireUser(user);
        if (teamId == null || teamId.isBlank()) {
            throw error(MvpErrorCode.VALIDATION_ERROR);
        }
        AgentTeamEntity team = teamMapper.selectOwnedById(user.tenantId(), user.userId(), teamId.trim());
        if (team == null) {
            throw error(MvpErrorCode.RESOURCE_NOT_FOUND);
        }

        // A team whose master went offline cannot plan, so fail loudly instead of silently
        // falling back to the platform default master.
        AgentRuntimeProfile master = agentRuntimeCatalogPort.loadOnlineProfile(user, team.getMasterAgentId());

        List<String> memberIds = memberMapper
            .selectOwnedMembersByTeam(user.tenantId(), user.userId(), team.getId()).stream()
            .map(AgentTeamMemberEntity::getAgentId)
            .filter(agentId -> !agentId.equals(team.getMasterAgentId()))
            .toList();
        List<AgentCapabilitySummary> candidates = memberIds.isEmpty()
            ? List.of()
            : agentRuntimeCatalogPort.listOnlineCandidates(user, memberIds);
        if (candidates.isEmpty()) {
            throw error(MvpErrorCode.NO_SUITABLE_AGENT);
        }

        MasterPersona persona = new MasterPersona(
            master.agentId(),
            master.name(),
            master.compiledSystemPromptTemplate(),
            master.resolvedModelName()
        );
        return new TeamRuntimeSelection(team.getId(), team.getName(), persona, candidates);
    }

    private void requireUser(CurrentUser user) {
        if (user == null || user.tenantId() == null || user.tenantId().isBlank()
            || user.userId() == null || user.userId().isBlank()) {
            throw error(MvpErrorCode.VALIDATION_ERROR);
        }
    }

    private Phase2ContractException error(MvpErrorCode code) {
        return new Phase2ContractException(code, code.name());
    }
}
