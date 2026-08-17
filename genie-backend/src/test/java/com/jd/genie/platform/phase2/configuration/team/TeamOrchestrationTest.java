package com.jd.genie.platform.phase2.configuration.team;

import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.contract.UserRole;
import com.jd.genie.platform.phase2.configuration.team.entity.AgentTeamEntity;
import com.jd.genie.platform.phase2.configuration.team.entity.AgentTeamMemberEntity;
import com.jd.genie.platform.phase2.configuration.team.mapper.AgentTeamMapper;
import com.jd.genie.platform.phase2.configuration.team.mapper.AgentTeamMemberMapper;
import com.jd.genie.platform.phase2.configuration.team.runtime.TeamRuntimeResolver;
import com.jd.genie.platform.phase2contract.dto.AgentCapabilitySummary;
import com.jd.genie.platform.phase2contract.dto.AgentRuntimeProfile;
import com.jd.genie.platform.phase2contract.dto.TeamRuntimeSelection;
import com.jd.genie.platform.phase2contract.error.Phase2ContractException;
import com.jd.genie.platform.phase2contract.port.AgentRuntimeCatalogPort;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TeamOrchestrationTest {
    private static final CurrentUser USER =
        new CurrentUser("tenant-a", "owner-a", "owner-a", "Owner A", UserRole.USER);
    private static final String TEAM_ID = "team-1";
    private static final String MASTER_ID = "agent-master";
    private static final String MEMBER_ID = "agent-member";
    private static final String PERSONA = "你是严谨的投研主管，先给结论再给证据。";

    private final AgentTeamMapper teamMapper = mock(AgentTeamMapper.class);
    private final AgentTeamMemberMapper memberMapper = mock(AgentTeamMemberMapper.class);
    private final AgentRuntimeCatalogPort catalogPort = mock(AgentRuntimeCatalogPort.class);
    private final TeamRuntimeResolver resolver =
        new TeamRuntimeResolver(teamMapper, memberMapper, catalogPort);

    @Test
    void resolvesMasterPersonaAndExcludesMasterFromCandidates() {
        when(teamMapper.selectOwnedById("tenant-a", "owner-a", TEAM_ID)).thenReturn(team());
        // A stale row may still list the master; the runtime must not offer it as a worker.
        when(memberMapper.selectOwnedMembersByTeam("tenant-a", "owner-a", TEAM_ID))
            .thenReturn(List.of(member(MEMBER_ID, 1), member(MASTER_ID, 2)));
        when(catalogPort.loadOnlineProfile(any(), eq(MASTER_ID))).thenReturn(masterProfile());
        when(catalogPort.listOnlineCandidates(any(), eq(List.of(MEMBER_ID))))
            .thenReturn(List.of(new AgentCapabilitySummary(MEMBER_ID, 1L, "研究员", "做调研")));

        TeamRuntimeSelection selection = resolver.resolve(USER, TEAM_ID);

        assertEquals(TEAM_ID, selection.teamId());
        assertEquals("投研小组", selection.teamName());
        assertEquals(
            List.of(MEMBER_ID),
            selection.memberCandidates().stream().map(AgentCapabilitySummary::agentId).toList()
        );
        assertEquals(MASTER_ID, selection.masterPersona().agentId());
        assertEquals("投研主管", selection.masterPersona().agentName());
        assertEquals(PERSONA, selection.masterPersona().personaPrompt());
        assertEquals("master-model", selection.masterPersona().modelName());
        assertTrue(selection.masterPersona().present());
    }

    @Test
    void failsWhenTeamIsNotOwnedByTheCaller() {
        when(teamMapper.selectOwnedById("tenant-a", "owner-a", TEAM_ID)).thenReturn(null);

        Phase2ContractException error =
            assertThrows(Phase2ContractException.class, () -> resolver.resolve(USER, TEAM_ID));

        assertEquals(MvpErrorCode.RESOURCE_NOT_FOUND, error.errorCode());
    }

    @Test
    void failsWhenMasterAgentIsOffline() {
        when(teamMapper.selectOwnedById("tenant-a", "owner-a", TEAM_ID)).thenReturn(team());
        when(catalogPort.loadOnlineProfile(any(), anyString()))
            .thenThrow(new Phase2ContractException(MvpErrorCode.AGENT_OFFLINE, "AGENT_OFFLINE"));

        Phase2ContractException error =
            assertThrows(Phase2ContractException.class, () -> resolver.resolve(USER, TEAM_ID));

        assertEquals(MvpErrorCode.AGENT_OFFLINE, error.errorCode());
    }

    @Test
    void failsWhenNoTeamMemberIsOnline() {
        when(teamMapper.selectOwnedById("tenant-a", "owner-a", TEAM_ID)).thenReturn(team());
        when(memberMapper.selectOwnedMembersByTeam("tenant-a", "owner-a", TEAM_ID))
            .thenReturn(List.of(member(MEMBER_ID, 1)));
        when(catalogPort.loadOnlineProfile(any(), eq(MASTER_ID))).thenReturn(masterProfile());
        when(catalogPort.listOnlineCandidates(any(), eq(List.of(MEMBER_ID)))).thenReturn(List.of());

        Phase2ContractException error =
            assertThrows(Phase2ContractException.class, () -> resolver.resolve(USER, TEAM_ID));

        assertEquals(MvpErrorCode.NO_SUITABLE_AGENT, error.errorCode());
    }

    private AgentTeamEntity team() {
        AgentTeamEntity entity = new AgentTeamEntity();
        entity.setId(TEAM_ID);
        entity.setTenantId("tenant-a");
        entity.setOwnerId("owner-a");
        entity.setName("投研小组");
        entity.setDescription("投研");
        entity.setMasterAgentId(MASTER_ID);
        entity.setVersion(0L);
        return entity;
    }

    private AgentTeamMemberEntity member(String agentId, int sortOrder) {
        AgentTeamMemberEntity member = new AgentTeamMemberEntity();
        member.setTenantId("tenant-a");
        member.setOwnerId("owner-a");
        member.setTeamId(TEAM_ID);
        member.setAgentId(agentId);
        member.setSortOrder(sortOrder);
        return member;
    }

    private AgentRuntimeProfile masterProfile() {
        return new AgentRuntimeProfile(
            MASTER_ID, 1L, "投研主管", "带队做投研", PERSONA, "master-model", List.of(), List.of());
    }
}
