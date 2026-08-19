package com.jd.genie.platform.phase2.runtime.resource;

import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.PageResponse;
import com.jd.genie.platform.phase2.configuration.agent.dto.AgentCreateRequest;
import com.jd.genie.platform.phase2.configuration.agent.dto.AgentResponse;
import com.jd.genie.platform.phase2.configuration.agent.dto.AgentSkillBindingRequest;
import com.jd.genie.platform.phase2.configuration.agent.service.AgentDefinitionService;
import com.jd.genie.platform.phase2.configuration.model.ModelCatalogService;
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
        boolean resource = containsAny(text, "团队", "小组", " team", "team ", "agent", "智能体", "代理");
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
        AgentResponse agent = createOnlineAgent(user, request.roles().get(0), available, query);
        return "资源已创建：Agent「" + agent.name() + "」（id=" + agent.id() + "），已绑定 "
                + agent.skillIds().size() + " 个 Skill 和 " + agent.capabilityKeys().size() + " 个 MCP 工具。当前对话不会自动切换 Agent。";
    }

    private String createTeam(CurrentUser user, Request request, Available available) {
        List<AgentResponse> agents = new ArrayList<>();
        for (RoleSpec role : request.roles()) {
            agents.add(createOnlineAgent(user, role, available, request.rawQuery()));
        }
        TeamResponse team = teamService.createTeam(user, new TeamCreateRequest(
                teamService.nextAvailableName(user, request.name()), request.description(), agents.get(0).id(),
                agents.subList(1, agents.size()).stream().map(AgentResponse::id).toList()
        ));
        int skills = agents.stream().mapToInt(agent -> agent.skillIds().size()).sum();
        int mcp = agents.stream().mapToInt(agent -> agent.capabilityKeys().size()).sum();
        return "资源已创建：Team「" + team.name() + "」（id=" + team.id() + "），主 Agent 为「"
                + agents.get(0).name() + "」，共 " + agents.size() + " 个 Agent；按最小权限共绑定 "
                + skills + " 个 Skill 和 " + mcp + " 个 MCP 工具。当前对话仍使用原 Team；如需使用新 Team，请在后续会话手动切换。";
    }

    private AgentResponse createOnlineAgent(
            CurrentUser user, RoleSpec role, Available available, String query
    ) {
        List<SkillResponse> skills = selectSkills(available.skills(), role.skillHint(), query);
        List<String> capabilities = selectCapabilities(available.mcpTools(), role.skillHint(), query);
        List<AgentSkillBindingRequest> bindings = new ArrayList<>();
        for (int index = 0; index < skills.size(); index++) {
            bindings.add(new AgentSkillBindingRequest(skills.get(index).id(), index + 1));
        }
        AgentResponse created = agentService.createAgent(user, new AgentCreateRequest(
                agentService.nextAvailableName(user, role.name()), role.description(), "RAW", null, role.systemPrompt(),
                ModelCatalogService.SYSTEM_DEFAULT, bindings, capabilities
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

    private List<SkillResponse> selectSkills(List<SkillResponse> candidates, String skillHint, String query) {
        return candidates.stream()
                .filter(skill -> matches(skillHint + " " + query, skill.name() + " " + skill.description() + " " + skill.instruction()))
                .limit(5)
                .toList();
    }

    private List<String> selectCapabilities(List<McpToolResponse> candidates, String skillHint, String query) {
        LinkedHashSet<String> selected = new LinkedHashSet<>();
        for (McpToolResponse tool : candidates) {
            if (matches(skillHint + " " + query, tool.toolName() + " " + nullToEmpty(tool.description()))) {
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

    private record RoleSpec(String name, String description, String systemPrompt, String skillHint) { }

    private record Request(boolean team, String name, String description, List<RoleSpec> roles, String rawQuery) {
        static Request from(String query) {
            String text = normalize(query);
            boolean team = containsAny(text, "团队", "小组", " team", "team ");
            if (containsAny(text, "软件", "开发", "code", "代码", "web", "前端", "后端")) {
                return software(team, query);
            }
            if (containsAny(text, "数据", "csv", "excel", "分析", "报表")) {
                return data(team, query);
            }
            if (containsAny(text, "研究", "调研", "竞品", "财报")) {
                return research(team, query);
            }
            return general(team, query);
        }

        private static Request data(boolean team, String query) {
            RoleSpec analyst = new RoleSpec(
                    "数据分析师",
                    "分析 CSV、表格等结构化数据：先检查字段与数据质量，再给出可复核的统计、趋势和结论。",
                    """
                    你是一名严谨的数据分析师。
                    接到任务后先确认数据来源、字段含义、缺失值和统计口径，发现质量问题要先说明。
                    然后做描述统计、对比和趋势分析；每条结论都必须能被数据支持。
                    把事实、推断和不确定项分开写，不要编造数字，也不要把猜测写成结论。
                    只使用当前已绑定的 Skill 和工具；缺数据或缺权限时明确说明，不要假装已经完成。
                    """.strip(),
                    "数据 csv 分析 报表 data"
            );
            if (!team) {
                return new Request(false, analyst.name(), analyst.description(), List.of(analyst), query);
            }
            return new Request(true, "数据分析团队",
                    "协作完成数据质量检查、统计分析和结论复核。",
                    List.of(
                            new RoleSpec(
                                    "分析负责人",
                                    "明确分析问题、统计口径和交付标准，把任务拆给数据分析师并汇总结论。",
                                    """
                                    你是数据分析团队的分析负责人。
                                    先把用户问题转成可执行的分析目标、口径和验收标准，再协调数据分析与复核。
                                    汇总时区分已证实的结论、待验证假设和数据缺口，不要跳过质量检查直接给结论。
                                    只使用当前已绑定的 Skill 和工具。
                                    """.strip(),
                                    "数据 csv 分析 报表 data"
                            ),
                            analyst,
                            new RoleSpec(
                                    "结果复核",
                                    "复核统计口径、结论是否被数据支持，标出遗漏和过度推断。",
                                    """
                                    你负责复核数据分析结论。
                                    检查口径是否一致、样本是否完整、结论是否被数据支持，并标出过度推断、遗漏和需要补做的分析。
                                    不要为了完整而编造结果；复核意见要具体、可执行。
                                    只使用当前已绑定的 Skill 和工具。
                                    """.strip(),
                                    "数据 csv 分析 报表 data"
                            )
                    ), query);
        }

        private static Request software(boolean team, String query) {
            RoleSpec developer = new RoleSpec(
                    "软件开发助手",
                    "根据需求拆解实现路径，编写或修改代码，并说明假设、风险和未完成项。",
                    """
                    你是一名务实的软件开发助手。
                    先确认目标、约束和现有代码/文件，再给出最小可行的实现方案。
                    代码要可运行、可复查：说明改了什么、为什么改、还有哪些未完成项。
                    不要虚构不存在的接口或文件；只使用当前已绑定的 Skill 和工具。
                    """.strip(),
                    "代码 前端 后端 github 测试 web api"
            );
            if (!team) {
                return new Request(false, developer.name(), developer.description(), List.of(developer), query);
            }
            return new Request(true, "软件开发团队",
                    "由技术负责人、研发和测试协作完成软件需求。",
                    List.of(
                            new RoleSpec(
                                    "技术负责人",
                                    "澄清需求与技术约束，拆分任务并验收交付。",
                                    """
                                    你是软件开发团队的技术负责人。
                                    先把用户需求转成可执行的范围、接口约定和验收标准，再协调后端、前端和测试。
                                    输出方案时写清假设、风险和未完成项，不要把未实现的能力说成已完成。
                                    只使用当前已绑定的 Skill 和工具。
                                    """.strip(),
                                    "代码 前端 后端 github 测试 web api"
                            ),
                            new RoleSpec(
                                    "后端开发",
                                    "实现服务端逻辑、数据和接口，保证行为可验证。",
                                    """
                                    你是后端开发。只处理服务端实现、数据和接口，不越权改前端或测试范围。
                                    给出可复查的改动说明，接口和错误处理要完整；不要虚构不存在的依赖。
                                    只使用当前已绑定的 Skill 和工具。
                                    """.strip(),
                                    "代码 后端 api github"
                            ),
                            new RoleSpec(
                                    "前端开发",
                                    "实现界面与交互，保证状态和接口调用清晰可测。",
                                    """
                                    你是前端开发。只处理界面、交互和前端状态，不越权改后端实现。
                                    优先按现有设计实现；说明页面结构、交互和未完成项。
                                    只使用当前已绑定的 Skill 和工具。
                                    """.strip(),
                                    "代码 前端 web"
                            ),
                            new RoleSpec(
                                    "测试工程师",
                                    "根据需求设计验证路径，报告缺陷和未覆盖风险。",
                                    """
                                    你是测试工程师。根据需求和实现设计验证路径，报告缺陷、回归风险和未覆盖场景。
                                    不要把未执行的测试写成已通过。只使用当前已绑定的 Skill 和工具。
                                    """.strip(),
                                    "测试 代码 github"
                            )
                    ), query);
        }

        private static Request research(boolean team, String query) {
            RoleSpec researcher = new RoleSpec(
                    "研究分析师",
                    "整理资料、比较证据，并输出区分事实与推断的结构化研究结论。",
                    """
                    你是一名研究分析师。
                    把事实、来源、推断和不确定性分开表达，不要把未经验证的内容写成事实。
                    结论要标明依据；找不到证据时明确说未知，而不是补全。
                    只使用当前已绑定的 Skill 和工具。
                    """.strip(),
                    "research report 文档 pdf"
            );
            if (!team) {
                return new Request(false, researcher.name(), researcher.description(), List.of(researcher), query);
            }
            return new Request(true, "研究报告团队",
                    "协作完成资料检索、分析整理和结论审校。",
                    List.of(
                            new RoleSpec(
                                    "研究负责人",
                                    "明确研究问题和交付结构，协调检索、分析与审校。",
                                    """
                                    你是研究报告团队的负责人。先明确问题、范围和交付结构，再汇总各角色产出。
                                    终稿必须区分事实、推断和待验证项。只使用当前已绑定的 Skill 和工具。
                                    """.strip(),
                                    "research report 文档"
                            ),
                            researcher,
                            new RoleSpec(
                                    "结果审校",
                                    "核对来源、表述和过度推断，确保结论可复核。",
                                    """
                                    你负责审校研究报告。核对来源是否支撑结论，标出过度推断、缺引用和表述含混之处。
                                    不要为了完整而补造证据。只使用当前已绑定的 Skill 和工具。
                                    """.strip(),
                                    "research report 文档 pdf"
                            )
                    ), query);
        }

        private static Request general(boolean team, String query) {
            RoleSpec assistant = new RoleSpec(
                    "通用助手",
                    "理解用户目标，在授权范围内完成任务，并清楚说明依据和未完成项。",
                    """
                    你是一个可靠的任务助手。
                    先确认用户目标和约束，再给出可执行的结果；说明依据、假设和未完成项。
                    不要越权访问未绑定的 Skill、工具或其他用户数据。
                    """.strip(),
                    "file web api"
            );
            if (!team) {
                return new Request(false, assistant.name(), assistant.description(), List.of(assistant), query);
            }
            return new Request(true, "协作团队",
                    "由协调、执行和复核角色协作完成用户交代的任务。",
                    List.of(
                            new RoleSpec(
                                    "协调负责人",
                                    "澄清目标、拆分任务并汇总各角色结果。",
                                    """
                                    你是协作团队的协调负责人。把用户目标拆成可执行任务，汇总执行结果和未完成项。
                                    只使用当前已绑定的 Skill 和工具。
                                    """.strip(),
                                    "file web api"
                            ),
                            new RoleSpec(
                                    "执行专员",
                                    "按拆分后的任务完成具体执行，并回报结果。",
                                    """
                                    你是执行专员。按协调负责人给出的任务完成具体工作，回报结果、依据和阻塞项。
                                    只使用当前已绑定的 Skill 和工具。
                                    """.strip(),
                                    "file web api"
                            ),
                            new RoleSpec(
                                    "结果复核",
                                    "检查交付是否满足目标，标出遗漏和风险。",
                                    """
                                    你负责复核交付是否满足用户目标，标出遗漏、风险和需要返工的点。
                                    只使用当前已绑定的 Skill 和工具。
                                    """.strip(),
                                    "file web api"
                            )
                    ), query);
        }
    }
}
