package com.jd.genie.platform.marketplace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.UserRole;
import com.jd.genie.platform.phase2.configuration.agent.dto.AgentResponse;
import com.jd.genie.platform.phase2.configuration.agent.service.AgentDefinitionService;
import com.jd.genie.platform.phase2.configuration.skill.dto.SkillResponse;
import com.jd.genie.platform.phase2.configuration.skill.service.SkillPackageImportService;
import com.jd.genie.platform.phase2.configuration.team.dto.TeamResponse;
import com.jd.genie.platform.phase2.configuration.team.service.AgentTeamService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MarketplaceInstallServiceTest {
    private static final CurrentUser USER = new CurrentUser("tenant-1", "user-1", "alice", "Alice", UserRole.USER);

    @Test
    void skillInstallUsesExistingPackageImportAndReturnsEnabledOwnedSkill() {
        SkillPackageImportService imports = mock(SkillPackageImportService.class);
        when(imports.importPackage(any(), any(), any())).thenReturn(skill("skill-1"));
        MarketplaceResourceService service = service(imports, mock(AgentDefinitionService.class), mock(AgentTeamService.class));

        MarketplaceInstallResponse result = service.install(USER, "skill-create-pdf-report");

        assertThat(result.resourceType()).isEqualTo(MarketplaceResourceType.SKILL);
        assertThat(result.createdSkillIds()).containsExactly("skill-1");
        assertThat(result.enabled()).isTrue();
    }

    @Test
    void teamInstallCreatesCurrentUsersAgentsAndThenUsesExistingTeamService() {
        SkillPackageImportService imports = mock(SkillPackageImportService.class);
        when(imports.importPackage(any(), any(), any())).thenReturn(skill("skill-1"), skill("skill-2"));
        AgentDefinitionService agents = mock(AgentDefinitionService.class);
        AtomicInteger sequence = new AtomicInteger();
        when(agents.createAgent(any(), any())).thenAnswer(invocation -> agent("agent-" + sequence.incrementAndGet(), "DRAFT", 0L));
        when(agents.onlineAgent(any(), any(), any())).thenAnswer(invocation -> agent((String) invocation.getArgument(1), "ONLINE", 1L));
        AgentTeamService teams = mock(AgentTeamService.class);
        when(teams.createTeam(any(), any())).thenReturn(new TeamResponse("team-1", "数据质量小组", "desc", "agent-1", "主 Agent",
            List.of("agent-2"), 0L, Instant.now(), Instant.now()));
        MarketplaceResourceService service = service(imports, agents, teams);

        MarketplaceInstallResponse result = service.install(USER, "team-data-quality");

        assertThat(result.createdTeamId()).isEqualTo("team-1");
        assertThat(result.createdAgentIds()).containsExactly("agent-1", "agent-2");
        assertThat(result.createdSkillIds()).containsExactly("skill-1", "skill-2");
        assertThat(result.enabled()).isTrue();
    }

    private MarketplaceResourceService service(SkillPackageImportService imports, AgentDefinitionService agents, AgentTeamService teams) {
        return new MarketplaceResourceService(new ObjectMapper(), imports, agents, teams, new MarketplacePackageArchiveService());
    }

    private SkillResponse skill(String id) {
        return new SkillResponse(id, "Skill", "desc", "instruction", "", "ENABLED", 0L, List.of(), Instant.now(), Instant.now());
    }

    private AgentResponse agent(String id, String status, Long version) {
        return new AgentResponse(id, "Agent", "desc", "RAW", null, "prompt", "default", status, version,
            List.of(), List.of(), Instant.now(), Instant.now());
    }
}
