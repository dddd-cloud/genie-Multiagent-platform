package com.jd.genie.platform.phase2.runtime.resource;

import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.PageResponse;
import com.jd.genie.platform.contract.UserRole;
import com.jd.genie.platform.phase2.configuration.agent.dto.AgentCreateRequest;
import com.jd.genie.platform.phase2.configuration.agent.dto.AgentResponse;
import com.jd.genie.platform.phase2.configuration.agent.service.AgentDefinitionService;
import com.jd.genie.platform.phase2.configuration.model.ModelCatalogService;
import com.jd.genie.platform.phase2.configuration.prompt.AgentPromptCompiler;
import com.jd.genie.platform.phase2.configuration.skill.dto.SkillResponse;
import com.jd.genie.platform.phase2.configuration.skill.service.SkillDefinitionService;
import com.jd.genie.platform.phase2.configuration.team.dto.TeamCreateRequest;
import com.jd.genie.platform.phase2.configuration.team.dto.TeamResponse;
import com.jd.genie.platform.phase2.configuration.team.service.AgentTeamService;
import com.jd.genie.platform.phase2.tooling.McpServerService;
import com.jd.genie.platform.phase2.tooling.McpToolResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
        assertFalse(SystemResourceBuilder.requiresResourceCreation(
                "请比较 REST 和 GraphQL，只给三点结论，不要创建任何 Agent 或 Team"));
        assertFalse(SystemResourceBuilder.requiresResourceCreation(
                "Compare REST and GraphQL; do not create an agent or team"));
        assertTrue(SystemResourceBuilder.requiresResourceCreation("不要创建 Agent，只创建 Team"));
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
        when(agents.nextAvailableName(eq(USER), any())).thenAnswer(call -> call.getArgument(1));
        when(teams.nextAvailableName(eq(USER), any())).thenAnswer(call -> call.getArgument(1));
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

        String result = builder(skills, mcps, agents, teams)
                .create(USER, "请创建一个数据分析团队");

        assertTrue(result.contains("team-1"));
        verify(skills).listSkills(USER, 1, 100);
        verify(mcps).capabilities(USER);
        ArgumentCaptor<AgentCreateRequest> create = ArgumentCaptor.forClass(AgentCreateRequest.class);
        verify(agents, times(3)).createAgent(eq(USER), create.capture());
        List<AgentCreateRequest> created = create.getAllValues();
        assertTrue(created.stream().allMatch(request ->
                ModelCatalogService.SYSTEM_DEFAULT.equals(request.modelName())));
        assertEquals(List.of("分析负责人", "数据分析师", "结果复核"),
                created.stream().map(AgentCreateRequest::name).toList());
        assertTrue(created.stream().noneMatch(request ->
                request.name().contains("·") || request.description().contains("系统资源构建器")));
        assertTrue(created.get(1).systemPrompt().contains("数据分析师"));
        ArgumentCaptor<TeamCreateRequest> teamCreate = ArgumentCaptor.forClass(TeamCreateRequest.class);
        verify(teams).createTeam(eq(USER), teamCreate.capture());
        assertEquals("数据分析团队", teamCreate.getValue().name());
        assertFalse(teamCreate.getValue().description().contains("系统资源构建器"));
    }

    @Test
    void createsASingleDataAgentWithoutDuplicatingTheRoleInTheName() {
        SkillDefinitionService skills = mock(SkillDefinitionService.class);
        McpServerService mcps = mock(McpServerService.class);
        AgentDefinitionService agents = mock(AgentDefinitionService.class);
        AgentTeamService teams = mock(AgentTeamService.class);
        when(skills.listSkills(USER, 1, 100)).thenReturn(new PageResponse<>(List.of(), 1, 100, false));
        when(mcps.capabilities(USER)).thenReturn(List.of());
        when(agents.nextAvailableName(eq(USER), any())).thenAnswer(call -> call.getArgument(1));
        when(agents.createAgent(eq(USER), any(AgentCreateRequest.class))).thenAnswer(call -> {
            AgentCreateRequest request = call.getArgument(1);
            return new AgentResponse("agent-1", request.name(), request.description(), "RAW", null,
                    request.systemPrompt(), "system-default", "DRAFT", 0L, List.of(), List.of(), Instant.now(), Instant.now());
        });
        when(agents.onlineAgent(eq(USER), any(), any())).thenAnswer(call -> {
            String id = call.getArgument(1);
            return new AgentResponse(id, "数据分析师", "", "RAW", null, "", "system-default", "ONLINE", 1L,
                    List.of(), List.of(), Instant.now(), Instant.now());
        });

        builder(skills, mcps, agents, teams)
                .create(USER, "帮我创建一个分析数据的agent");

        ArgumentCaptor<AgentCreateRequest> create = ArgumentCaptor.forClass(AgentCreateRequest.class);
        verify(agents).createAgent(eq(USER), create.capture());
        verify(teams, never()).createTeam(any(), any());
        AgentCreateRequest request = create.getValue();
        assertEquals("数据分析师", request.name());
        assertTrue(request.description().contains("结构化数据"));
        assertFalse(request.description().contains("系统资源构建器"));
        assertTrue(request.systemPrompt().contains("数据分析师"));
        assertTrue(request.systemPrompt().contains("统计口径"));
    }

    @Test
    void suffixesTheAgentNameWhenThePreferredNameIsAlreadyTaken() {
        SkillDefinitionService skills = mock(SkillDefinitionService.class);
        McpServerService mcps = mock(McpServerService.class);
        AgentDefinitionService agents = mock(AgentDefinitionService.class);
        AgentTeamService teams = mock(AgentTeamService.class);
        when(skills.listSkills(USER, 1, 100)).thenReturn(new PageResponse<>(List.of(), 1, 100, false));
        when(mcps.capabilities(USER)).thenReturn(List.of());
        when(agents.nextAvailableName(eq(USER), eq("数据分析师"))).thenReturn("数据分析师 (2)");
        when(agents.createAgent(eq(USER), any(AgentCreateRequest.class))).thenAnswer(call -> {
            AgentCreateRequest request = call.getArgument(1);
            return new AgentResponse("agent-2", request.name(), request.description(), "RAW", null,
                    request.systemPrompt(), "system-default", "DRAFT", 0L, List.of(), List.of(), Instant.now(), Instant.now());
        });
        when(agents.onlineAgent(eq(USER), any(), any())).thenReturn(new AgentResponse(
                "agent-2", "数据分析师 (2)", "", "RAW", null, "", "system-default", "ONLINE", 1L,
                List.of(), List.of(), Instant.now(), Instant.now()));

        builder(skills, mcps, agents, teams)
                .create(USER, "帮我创建一个分析数据的agent");

        ArgumentCaptor<AgentCreateRequest> create = ArgumentCaptor.forClass(AgentCreateRequest.class);
        verify(agents).createAgent(eq(USER), create.capture());
        assertEquals("数据分析师 (2)", create.getValue().name());
    }

    @Test
    void reusesMatchingOnlineAgentsAndCreatesOnlyMissingRoles() {
        SkillDefinitionService skills = mock(SkillDefinitionService.class);
        McpServerService mcps = mock(McpServerService.class);
        AgentDefinitionService agents = mock(AgentDefinitionService.class);
        AgentTeamService teams = mock(AgentTeamService.class);
        AgentResponse backend = new AgentResponse("backend-1", "后端工程师", "", "RAW", null, "", "system-default", "ONLINE", 1L, List.of(), List.of(), Instant.now(), Instant.now());
        when(skills.listSkills(USER, 1, 100)).thenReturn(new PageResponse<>(List.of(), 1, 100, false));
        when(mcps.capabilities(USER)).thenReturn(List.of());
        when(agents.listAgents(USER, 1, 100)).thenReturn(new PageResponse<>(List.of(backend), 1, 100, false));
        when(agents.nextAvailableName(eq(USER), any())).thenAnswer(call -> call.getArgument(1));
        AtomicInteger sequence = new AtomicInteger();
        when(agents.createAgent(eq(USER), any(AgentCreateRequest.class))).thenAnswer(call -> {
            int id = sequence.incrementAndGet(); AgentCreateRequest request = call.getArgument(1);
            return new AgentResponse("new-" + id, request.name(), request.description(), "RAW", null, request.systemPrompt(), "system-default", "DRAFT", 0L, List.of(), List.of(), Instant.now(), Instant.now());
        });
        when(agents.onlineAgent(eq(USER), any(), any())).thenAnswer(call -> new AgentResponse(call.getArgument(1), "new", "", "RAW", null, "", "system-default", "ONLINE", 1L, List.of(), List.of(), Instant.now(), Instant.now()));
        when(teams.nextAvailableName(eq(USER), any())).thenAnswer(call -> call.getArgument(1));
        when(teams.createTeam(eq(USER), any())).thenReturn(new TeamResponse("team-1", "软件开发团队", "", "new-1", "", List.of(), 0L, Instant.now(), Instant.now()));

        String result = builder(skills, mcps, agents, teams).create(USER, "创建一个软件开发团队");

        verify(agents, times(3)).createAgent(eq(USER), any(AgentCreateRequest.class));
        assertTrue(result.contains("复用 1 个既有 Agent，新建 3 个"));
    }

    @Test
    void honorsRequestedTeamSizeBetweenTwoAndTwenty() {
        SkillDefinitionService skills = mock(SkillDefinitionService.class);
        McpServerService mcps = mock(McpServerService.class);
        AgentDefinitionService agents = mock(AgentDefinitionService.class);
        AgentTeamService teams = mock(AgentTeamService.class);
        when(skills.listSkills(USER, 1, 100)).thenReturn(new PageResponse<>(List.of(), 1, 100, false));
        when(mcps.capabilities(USER)).thenReturn(List.of());
        when(agents.nextAvailableName(eq(USER), any())).thenAnswer(call -> call.getArgument(1));
        AtomicInteger sequence = new AtomicInteger();
        when(agents.createAgent(eq(USER), any(AgentCreateRequest.class))).thenAnswer(call -> {
            int id = sequence.incrementAndGet(); AgentCreateRequest request = call.getArgument(1);
            return new AgentResponse("agent-" + id, request.name(), request.description(), "RAW", null, request.systemPrompt(), "system-default", "DRAFT", 0L, List.of(), List.of(), Instant.now(), Instant.now());
        });
        when(agents.onlineAgent(eq(USER), any(), any())).thenAnswer(call -> new AgentResponse(call.getArgument(1), "online", "", "RAW", null, "", "system-default", "ONLINE", 1L, List.of(), List.of(), Instant.now(), Instant.now()));
        when(teams.nextAvailableName(eq(USER), any())).thenAnswer(call -> call.getArgument(1));
        when(teams.createTeam(eq(USER), any())).thenReturn(new TeamResponse("team-10", "软件开发团队", "", "agent-1", "", List.of(), 0L, Instant.now(), Instant.now()));

        builder(skills, mcps, agents, teams).create(USER, "创建一个由 10 个 Agent 组成的软件开发团队");

        verify(agents, times(10)).createAgent(eq(USER), any(AgentCreateRequest.class));
    }

    @Test
    void honorsQuotedTeamNameAndFourPersonCountWithoutReadingDateDigitsAsCount() {
        SkillDefinitionService skills = mock(SkillDefinitionService.class);
        McpServerService mcps = mock(McpServerService.class);
        AgentDefinitionService agents = mock(AgentDefinitionService.class);
        AgentTeamService teams = mock(AgentTeamService.class);
        AgentResponse lead = onlineAgent("lead", "技术负责人", "负责技术方案和验收");
        AgentResponse backend = onlineAgent("backend", "后端开发", "负责服务端接口");
        AgentResponse frontend = onlineAgent("frontend", "前端开发", "负责界面交互");
        AgentResponse qa = onlineAgent("qa", "测试工程师", "负责质量保障");
        when(skills.listSkills(USER, 1, 100)).thenReturn(new PageResponse<>(List.of(), 1, 100, false));
        when(mcps.capabilities(USER)).thenReturn(List.of());
        when(agents.listAgents(USER, 1, 100)).thenReturn(
                new PageResponse<>(List.of(lead, backend, frontend, qa), 1, 100, false));
        when(teams.nextAvailableName(USER, "QA-E2E-复用团队-20260819"))
                .thenReturn("QA-E2E-复用团队-20260819");
        when(teams.createTeam(eq(USER), any(TeamCreateRequest.class))).thenReturn(new TeamResponse(
                "team-four", "QA-E2E-复用团队-20260819", "", "lead", "技术负责人",
                List.of("backend", "frontend", "qa"), 0L, Instant.now(), Instant.now()));

        String result = builder(skills, mcps, agents, teams).create(USER,
                "请一键创建一个名为“QA-E2E-复用团队-20260819”的软件开发团队，" +
                        "团队总人数必须正好为 4 人，必须包含技术负责人、后端开发、前端开发、测试工程师");

        verify(agents, never()).createAgent(eq(USER), any(AgentCreateRequest.class));
        ArgumentCaptor<TeamCreateRequest> created = ArgumentCaptor.forClass(TeamCreateRequest.class);
        verify(teams).createTeam(eq(USER), created.capture());
        assertEquals("QA-E2E-复用团队-20260819", created.getValue().name());
        assertEquals("lead", created.getValue().masterAgentId());
        assertEquals(List.of("backend", "frontend", "qa"), created.getValue().memberAgentIds());
        assertTrue(result.contains("共 4 个 Agent"));
        assertTrue(result.contains("复用 4 个既有 Agent，新建 0 个"));
    }

    @Test
    void skipsAValidButOversizedCombinedSkillPromptInsteadOfFailingTeamCreation() {
        SkillDefinitionService skills = mock(SkillDefinitionService.class);
        McpServerService mcps = mock(McpServerService.class);
        AgentDefinitionService agents = mock(AgentDefinitionService.class);
        AgentTeamService teams = mock(AgentTeamService.class);
        SkillResponse small = new SkillResponse("small", "代码助手", "代码", "帮助编写代码。", "", "ENABLED", 1L,
                List.of(), Instant.now(), Instant.now());
        SkillResponse huge = new SkillResponse("huge", "代码规范", "代码", "x".repeat(19_800), "", "ENABLED", 1L,
                List.of(), Instant.now(), Instant.now());
        when(skills.listSkills(USER, 1, 100)).thenReturn(new PageResponse<>(List.of(small, huge), 1, 100, false));
        when(mcps.capabilities(USER)).thenReturn(List.of());
        when(agents.nextAvailableName(eq(USER), any())).thenAnswer(call -> call.getArgument(1));
        when(agents.createAgent(eq(USER), any(AgentCreateRequest.class))).thenAnswer(call -> {
            AgentCreateRequest request = call.getArgument(1);
            return new AgentResponse("agent-1", request.name(), request.description(), "RAW", null, request.systemPrompt(),
                    "system-default", "DRAFT", 0L, request.skills().stream().map(item -> item.skillId()).toList(), List.of(), Instant.now(), Instant.now());
        });
        when(agents.onlineAgent(eq(USER), any(), any())).thenAnswer(call -> new AgentResponse(call.getArgument(1), "online", "", "RAW", null, "", "system-default", "ONLINE", 1L, List.of(), List.of(), Instant.now(), Instant.now()));

        builder(skills, mcps, agents, teams).create(USER, "创建一个软件开发 agent");

        ArgumentCaptor<AgentCreateRequest> created = ArgumentCaptor.forClass(AgentCreateRequest.class);
        verify(agents).createAgent(eq(USER), created.capture());
        assertEquals(List.of("small"), created.getValue().skills().stream().map(item -> item.skillId()).toList());
    }

    private SystemResourceBuilder builder(SkillDefinitionService skills, McpServerService mcps,
                                          AgentDefinitionService agents, AgentTeamService teams) {
        return new SystemResourceBuilder(skills, mcps, agents, teams, new AgentPromptCompiler());
    }

    private AgentResponse onlineAgent(String id, String name, String description) {
        return new AgentResponse(id, name, description, "RAW", null, "", "system-default", "ONLINE", 1L,
                List.of(), List.of(), Instant.now(), Instant.now());
    }
}
