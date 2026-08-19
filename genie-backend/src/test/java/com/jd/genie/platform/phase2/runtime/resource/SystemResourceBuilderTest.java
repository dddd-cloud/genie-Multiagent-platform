package com.jd.genie.platform.phase2.runtime.resource;

import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.PageResponse;
import com.jd.genie.platform.contract.UserRole;
import com.jd.genie.platform.phase2.configuration.agent.dto.AgentCreateRequest;
import com.jd.genie.platform.phase2.configuration.agent.dto.AgentResponse;
import com.jd.genie.platform.phase2.configuration.agent.service.AgentDefinitionService;
import com.jd.genie.platform.phase2.configuration.skill.dto.SkillResponse;
import com.jd.genie.platform.phase2.configuration.skill.service.SkillDefinitionService;
import com.jd.genie.platform.phase2.configuration.team.dto.TeamCreateRequest;
import com.jd.genie.platform.phase2.configuration.team.dto.TeamResponse;
import com.jd.genie.platform.phase2.configuration.team.service.AgentTeamService;
import com.jd.genie.platform.phase2.tooling.McpServerService;
import com.jd.genie.platform.phase2.tooling.McpToolResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SystemResourceBuilderTest {
    private static final CurrentUser USER = new CurrentUser("tenant-a", "user-a", "alice", "Alice", UserRole.USER);

    @Test
    void detectsCreationRequestsWithoutTreatingNormalWorkAsAResourceRequest() {
        assertTrue(SystemResourceBuilder.requiresResourceCreation("给我一键创建一个软件开发团队并生成一个软件"));
        assertTrue(SystemResourceBuilder.requiresResourceCreation("create a data analysis agent"));
        assertFalse(SystemResourceBuilder.requiresResourceCreation("请用现有团队生成一个软件"));
        assertFalse(SystemResourceBuilder.requiresResourceCreation("分析这个 CSV"));
    }

    @Test
    void createsUserOwnedDataTeamWithOnlyRelevantEnabledResources() {
        SkillDefinitionService skills = mock(SkillDefinitionService.class);
        McpServerService mcps = mock(McpServerService.class);
        AgentDefinitionService agents = mock(AgentDefinitionService.class);
        AgentTeamService teams = mock(AgentTeamService.class);
        SkillResponse csv = new SkillResponse("skill-csv", "CSV 数据摘要", "数据统计", "分析 CSV 数据", "", "ENABLED", 1L,
                List.of(), Instant.now(), Instant.now());
        SkillResponse disabled = new SkillResponse("skill-off", "PDF", "报告", "生成 PDF", "", "DISABLED", 1L,
                List.of(), Instant.now(), Instant.now());
        when(skills.listSkills(USER, 1, 100)).thenReturn(new PageResponse<>(List.of(csv, disabled), 1, 100, false));
        when(mcps.capabilities(USER)).thenReturn(List.of(new McpToolResponse("tool-data", "data_query", "data_query", "数据分析工具", null, true, true, 0L)));
        AtomicInteger sequence = new AtomicInteger();
        when(agents.createAgent(eq(USER), any(AgentCreateRequest.class))).thenAnswer(call -> {
            int value = sequence.incrementAndGet();
            AgentCreateRequest request = call.getArgument(1);
            return new AgentResponse("agent-" + value, request.name(), request.description(), "RAW", null,
                    request.systemPrompt(), "default", "DRAFT", 0L, request.skills().stream().map(item -> item.skillId()).toList(),
                    request.capabilityKeys(), Instant.now(), Instant.now());
        });
        when(agents.onlineAgent(eq(USER), any(), any())).thenAnswer(call -> {
            String id = call.getArgument(1);
            return new AgentResponse(id, id, "", "RAW", null, "", "default", "ONLINE", 1L,
                    List.of("skill-csv"), List.of("mcp:tool-data"), Instant.now(), Instant.now());
        });
        when(teams.createTeam(eq(USER), any(TeamCreateRequest.class))).thenReturn(new TeamResponse(
                "team-1", "数据分析团队", "", "agent-1", "agent-1", List.of("agent-2", "agent-3"), 0L, Instant.now(), Instant.now()));

        String result = new SystemResourceBuilder(skills, mcps, agents, teams)
                .create(USER, "请创建一个数据分析团队");

        assertTrue(result.contains("team-1"));
        verify(skills).listSkills(USER, 1, 100);
        verify(mcps).capabilities(USER);
        verify(agents, times(3)).createAgent(eq(USER), any(AgentCreateRequest.class));
        verify(teams).createTeam(eq(USER), any(TeamCreateRequest.class));
    }
}
