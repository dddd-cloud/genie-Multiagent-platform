package com.jd.genie.platform.marketplace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.UserRole;
import com.jd.genie.platform.phase2.configuration.agent.dto.AgentCreateRequest;
import com.jd.genie.platform.phase2.configuration.agent.dto.AgentResponse;
import com.jd.genie.platform.phase2.configuration.agent.service.AgentDefinitionService;
import com.jd.genie.platform.phase2.configuration.model.ModelCatalogService;
import com.jd.genie.platform.phase2.configuration.skill.dto.SkillResponse;
import com.jd.genie.platform.phase2.configuration.skill.service.SkillPackageImportService;
import com.jd.genie.platform.phase2.configuration.team.dto.TeamResponse;
import com.jd.genie.platform.phase2.configuration.team.service.AgentTeamService;
import com.jd.genie.platform.phase2.tooling.AuthType;
import com.jd.genie.platform.phase2.tooling.McpServerResponse;
import com.jd.genie.platform.phase2.tooling.McpServerService;
import com.jd.genie.platform.phase2.tooling.McpServerStatus;
import com.jd.genie.platform.phase2.tooling.McpToolResponse;
import com.jd.genie.platform.phase2.tooling.UpdateToolEnabledRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
        when(agents.nextAvailableName(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        AtomicInteger sequence = new AtomicInteger();
        when(agents.createAgent(any(), any())).thenAnswer(invocation -> agent("agent-" + sequence.incrementAndGet(), "DRAFT", 0L));
        when(agents.onlineAgent(any(), any(), any())).thenAnswer(invocation -> agent((String) invocation.getArgument(1), "ONLINE", 1L));
        AgentTeamService teams = mock(AgentTeamService.class);
        when(teams.nextAvailableName(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(teams.createTeam(any(), any())).thenReturn(new TeamResponse("team-1", "数据质量小组", "desc", "agent-1", "主 Agent",
            List.of("agent-2"), 0L, Instant.now(), Instant.now()));
        MarketplaceResourceService service = service(imports, agents, teams);

        MarketplaceInstallResponse result = service.install(USER, "team-data-quality");

        ArgumentCaptor<AgentCreateRequest> create = ArgumentCaptor.forClass(AgentCreateRequest.class);
        verify(agents, atLeastOnce()).createAgent(any(), create.capture());
        assertThat(create.getAllValues()).isNotEmpty();
        assertThat(create.getAllValues()).allMatch(request -> ModelCatalogService.SYSTEM_DEFAULT.equals(request.modelName()));
        assertThat(result.createdTeamId()).isEqualTo("team-1");
        assertThat(result.createdAgentIds()).containsExactly("agent-1", "agent-2");
        assertThat(result.createdSkillIds()).containsExactly("skill-1");
        assertThat(result.enabled()).isTrue();
    }

    @Test
    void noAuthMcpInstallReusesAnAlreadyEnabledOwnedServer() {
        McpServerService mcps = mock(McpServerService.class);
        McpServerResponse existing = mcp("mcp-1", McpServerStatus.ENABLED, 2L);
        when(mcps.list()).thenReturn(List.of(existing));
        MarketplaceResourceService service = service(mock(SkillPackageImportService.class),
            mock(AgentDefinitionService.class), mock(AgentTeamService.class), mcps);

        MarketplaceInstallResponse result = service.install(USER, "mcp-himalayas-remote-jobs");

        assertThat(result.primaryResourceId()).isEqualTo("mcp-1");
        assertThat(result.enabled()).isTrue();
        assertThat(result.warnings()).containsExactly("该 MCP 已存在，未重复创建。");
        verify(mcps, never()).create(any());
    }

    @Test
    void noAuthMcpInstallDiscoversToolsBeforeEnabling() {
        McpServerService mcps = mock(McpServerService.class);
        when(mcps.list()).thenReturn(List.of());
        when(mcps.create(any())).thenReturn(mcp("mcp-2", McpServerStatus.DRAFT, 0L));
        when(mcps.refreshTools("mcp-2")).thenReturn(List.of(
            new McpToolResponse("tool-1", "get_jobs", "runtime-1", "Read jobs",
                new ObjectMapper().createObjectNode(), true, true, 0L),
            new McpToolResponse("tool-2", "post_job_public", "runtime-2", "Post a paid job",
                new ObjectMapper().createObjectNode(), true, true, 0L)));
        when(mcps.setStatus("mcp-2", 0L, McpServerStatus.ENABLED))
            .thenReturn(mcp("mcp-2", McpServerStatus.ENABLED, 1L));
        MarketplaceResourceService service = service(mock(SkillPackageImportService.class),
            mock(AgentDefinitionService.class), mock(AgentTeamService.class), mcps);

        MarketplaceInstallResponse result = service.install(USER, "mcp-himalayas-remote-jobs");

        assertThat(result.enabled()).isTrue();
        verify(mcps).refreshTools("mcp-2");
        ArgumentCaptor<UpdateToolEnabledRequest> update = ArgumentCaptor.forClass(UpdateToolEnabledRequest.class);
        verify(mcps).setToolEnabled(org.mockito.ArgumentMatchers.eq("mcp-2"),
            org.mockito.ArgumentMatchers.eq("tool-2"), update.capture());
        assertThat(update.getValue().enabled()).isFalse();
        verify(mcps).setStatus("mcp-2", 0L, McpServerStatus.ENABLED);
    }

    private MarketplaceResourceService service(SkillPackageImportService imports, AgentDefinitionService agents, AgentTeamService teams) {
        return new MarketplaceResourceService(new ObjectMapper(), imports, agents, teams, new MarketplacePackageArchiveService());
    }

    private MarketplaceResourceService service(SkillPackageImportService imports, AgentDefinitionService agents,
                                                AgentTeamService teams, McpServerService mcps) {
        return new MarketplaceResourceService(new ObjectMapper(), imports, agents, teams,
            new MarketplacePackageArchiveService(), mcps);
    }

    private SkillResponse skill(String id) {
        return new SkillResponse(id, "Skill", "desc", "instruction", "", "ENABLED", 0L, List.of(), Instant.now(), Instant.now());
    }

    private AgentResponse agent(String id, String status, Long version) {
        return new AgentResponse(id, "Agent", "desc", "RAW", null, "prompt", "default", status, version,
            List.of(), List.of(), Instant.now(), Instant.now());
    }

    private McpServerResponse mcp(String id, McpServerStatus status, long version) {
        return new McpServerResponse(id, "Himalayas", "https://mcp.himalayas.app/sse", AuthType.NONE,
            null, status, false, "SUCCESS", null, null, version,
            java.time.LocalDateTime.now(), java.time.LocalDateTime.now());
    }
}
