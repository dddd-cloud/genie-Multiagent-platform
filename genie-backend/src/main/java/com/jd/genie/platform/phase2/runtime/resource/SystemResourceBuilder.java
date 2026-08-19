package com.jd.genie.platform.phase2.runtime.resource;

import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.PageResponse;
import com.jd.genie.platform.phase2.configuration.agent.dto.AgentCreateRequest;
import com.jd.genie.platform.phase2.configuration.agent.dto.AgentResponse;
import com.jd.genie.platform.phase2.configuration.agent.dto.AgentSkillBindingRequest;
import com.jd.genie.platform.phase2.configuration.agent.service.AgentDefinitionService;
import com.jd.genie.platform.phase2.configuration.skill.dto.SkillResponse;
import com.jd.genie.platform.phase2.configuration.skill.service.SkillDefinitionService;
import com.jd.genie.platform.phase2.configuration.team.dto.TeamCreateRequest;
import com.jd.genie.platform.phase2.configuration.team.dto.TeamResponse;
import com.jd.genie.platform.phase2.configuration.team.service.AgentTeamService;
import com.jd.genie.platform.phase2.tooling.McpServerService;
import com.jd.genie.platform.phase2.tooling.McpToolResponse;
import com.jd.genie.platform.phase2contract.capability.CapabilityKeys;
import com.jd.genie.platform.phase2contract.dto.AgentCapabilitySummary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * A non-persisted orchestration-only Agent.  It is deliberately never exposed by
 * Agent CRUD APIs: the only way to reach it is the guarded orchestration step.
 */
@Service
public class SystemResourceBuilder {
    public static final String AGENT_ID = "__system_resource_builder__";
    private static final AgentCapabilitySummary CANDIDATE = new AgentCapabilitySummary(
            AGENT_ID, 1L, "系统资源构建器",
            "仅用于创建当前用户自己的 Agent 或 Team；按最小权限读取可用 Skill/MCP 并完成绑定。"
    );

    private final SkillDefinitionService skillService;
    private final McpServerService mcpServerService;
    private final AgentDefinitionService agentService;
    private final AgentTeamService teamService;

    public SystemResourceBuilder(
            SkillDefinitionService skillService,
            McpServerService mcpServerService,
            AgentDefinitionService agentService,
            AgentTeamService teamService
    ) {
        this.skillService = skillService;
        this.mcpServerService = mcpServerService;
        this.agentService = agentService;
        this.teamService = teamService;
    }

    public static boolean requiresResourceCreation(String query) {
        String text = normalize(query);
        if (text.isBlank()) return false;
        boolean create = containsAny(text, "创建", "新建", "生成", "组建", "搭建", "帮我建", "一键建", "create ", "build ");
        boolean resource = containsAny(text, "团队", " team", "team ", "agent", "智能体", "代理");
        return create && resource;
    }

    public static AgentCapabilitySummary candidate() {
        return CANDIDATE;
    }

    public static boolean isSystemAgent(String agentId) {
        return AGENT_ID.equals(agentId);
    }

    @Transactional
    public String create(CurrentUser user, String query) {
        Request request = Request.from(query);
        Available available = availableFor(user);
        if (request.team()) {
            return createTeam(user, request, available);
        }
        AgentResponse agent = createOnlineAgent(user, request.name(), request.description(), request.roles().get(0), available, query);
        return "资源已创建：Agent「" + agent.name() + "」（id=" + agent.id() + "），已绑定 "
                + agent.skillIds().size() + " 个 Skill 和 " + agent.capabilityKeys().size() + " 个 MCP 工具。当前对话不会自动切换 Agent。";
    }

    private String createTeam(CurrentUser user, Request request, Available available) {
        List<AgentResponse> agents = new ArrayList<>();
        for (String role : request.roles()) {
            agents.add(createOnlineAgent(user, request.name(), request.description(), role, available, request.rawQuery()));
        }
        TeamResponse team = teamService.createTeam(user, new TeamCreateRequest(
                request.name(), request.description(), agents.get(0).id(),
                agents.subList(1, agents.size()).stream().map(AgentResponse::id).toList()
        ));
        int skills = agents.stream().mapToInt(agent -> agent.skillIds().size()).sum();
        int mcp = agents.stream().mapToInt(agent -> agent.capabilityKeys().size()).sum();
        return "资源已创建：Team「" + team.name() + "」（id=" + team.id() + "），主 Agent 为「"
                + agents.get(0).name() + "」，共 " + agents.size() + " 个 Agent；按最小权限共绑定 "
                + skills + " 个 Skill 和 " + mcp + " 个 MCP 工具。当前对话仍使用原 Team；如需使用新 Team，请在后续会话手动切换。";
    }

    private AgentResponse createOnlineAgent(
            CurrentUser user, String teamName, String teamDescription, String role, Available available, String query
    ) {
        List<SkillResponse> skills = selectSkills(available.skills(), role, query);
        List<String> capabilities = selectCapabilities(available.mcpTools(), role, query);
        List<AgentSkillBindingRequest> bindings = new ArrayList<>();
        for (int index = 0; index < skills.size(); index++) {
            bindings.add(new AgentSkillBindingRequest(skills.get(index).id(), index + 1));
        }
        String name = teamName + " · " + role;
        String prompt = "你是「" + role + "」。你属于「" + teamName + "」。"
                + "只处理与该角色相关、且用户明确授权的工作；输出可复核的结果、假设和未完成项。"
                + "不要访问未绑定的 Skill、MCP 或其他用户的数据。";
        AgentResponse created = agentService.createAgent(user, new AgentCreateRequest(
                name, teamDescription, "RAW", null, prompt, "default", bindings, capabilities
        ));
        return agentService.onlineAgent(user, created.id(), created.version());
    }

    private Available availableFor(CurrentUser user) {
        PageResponse<SkillResponse> page = skillService.listSkills(user, 1, 100);
        List<SkillResponse> skills = page.items().stream()
                .filter(skill -> "ENABLED".equals(skill.status()))
                .toList();
        return new Available(skills, mcpServerService.capabilities(user));
    }

    private List<SkillResponse> selectSkills(List<SkillResponse> candidates, String role, String query) {
        return candidates.stream()
                .filter(skill -> matches(role + " " + query, skill.name() + " " + skill.description() + " " + skill.instruction()))
                .limit(5)
                .toList();
    }

    private List<String> selectCapabilities(List<McpToolResponse> candidates, String role, String query) {
        LinkedHashSet<String> selected = new LinkedHashSet<>();
        for (McpToolResponse tool : candidates) {
            if (matches(role + " " + query, tool.toolName() + " " + nullToEmpty(tool.description()))) {
                selected.add(CapabilityKeys.forMcpTool(tool.id()));
            }
            if (selected.size() == 5) break;
        }
        return List.copyOf(selected);
    }

    private static boolean matches(String request, String resource) {
        String left = normalize(request);
        String right = normalize(resource);
        for (String keyword : List.of("python", "数据", "csv", "分析", "报表", "pdf", "文档", "前端", "后端", "代码", "github", "测试", "research", "report", "data", "file", "web", "api")) {
            if (left.contains(keyword) && right.contains(keyword)) return true;
        }
        return false;
    }

    private static boolean containsAny(String value, String... values) {
        for (String item : values) if (value.contains(item)) return true;
        return false;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }

    private static String nullToEmpty(String value) { return value == null ? "" : value; }

    private record Available(List<SkillResponse> skills, List<McpToolResponse> mcpTools) { }

    private record Request(boolean team, String name, String description, List<String> roles, String rawQuery) {
        static Request from(String query) {
            String text = normalize(query);
            boolean team = containsAny(text, "团队", " team", "team ");
            if (containsAny(text, "软件", "开发", "code", "代码", "web", "前端", "后端")) {
                return new Request(team, team ? "软件开发团队" : "软件开发 Agent", "由系统资源构建器根据当前用户资源创建", team
                        ? List.of("技术负责人", "后端开发", "前端开发", "测试工程师") : List.of("软件开发"), query);
            }
            if (containsAny(text, "数据", "csv", "分析", "报表")) {
                return new Request(team, team ? "数据分析团队" : "数据分析 Agent", "由系统资源构建器根据当前用户资源创建", team
                        ? List.of("分析负责人", "数据分析", "结果复核") : List.of("数据分析"), query);
            }
            return new Request(team, team ? "协作团队" : "通用 Agent", "由系统资源构建器根据当前用户资源创建", team
                    ? List.of("协调负责人", "执行专员", "结果复核") : List.of("通用助理"), query);
        }
    }
}
