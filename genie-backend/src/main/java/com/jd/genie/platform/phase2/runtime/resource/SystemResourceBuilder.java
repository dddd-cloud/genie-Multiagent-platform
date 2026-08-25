package com.jd.genie.platform.phase2.runtime.resource;

import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.PageResponse;
import com.jd.genie.platform.phase2.configuration.agent.dto.AgentCreateRequest;
import com.jd.genie.platform.phase2.configuration.agent.dto.AgentResponse;
import com.jd.genie.platform.phase2.configuration.agent.dto.AgentSkillBindingRequest;
import com.jd.genie.platform.phase2.configuration.agent.service.AgentDefinitionService;
import com.jd.genie.platform.phase2.configuration.model.ModelCatalogService;
import com.jd.genie.platform.phase2.configuration.prompt.AgentPromptCompiler;
import com.jd.genie.platform.phase2.configuration.prompt.PromptCompilationRequest;
import com.jd.genie.platform.phase2.configuration.prompt.PromptSkillFragment;
import com.jd.genie.platform.phase2.configuration.prompt.PromptValidationException;
import com.jd.genie.platform.phase2.configuration.skill.dto.SkillResponse;
import com.jd.genie.platform.phase2.configuration.skill.service.SkillDefinitionService;
import com.jd.genie.platform.phase2.configuration.team.dto.TeamCreateRequest;
import com.jd.genie.platform.phase2.configuration.team.dto.TeamResponse;
import com.jd.genie.platform.phase2.configuration.team.service.AgentTeamService;
import com.jd.genie.platform.conversation.attachment.ChatAttachmentPrompt;
import com.jd.genie.platform.phase2.tooling.McpServerService;
import com.jd.genie.platform.phase2.tooling.McpToolResponse;
import com.jd.genie.platform.phase2.runtime.orchestration.OrchestrationModelPort;
import com.jd.genie.platform.phase2contract.capability.CapabilityKeys;
import com.jd.genie.platform.phase2contract.dto.AgentCapabilitySummary;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A non-persisted orchestration-only Agent.  It is deliberately never exposed by
 * Agent CRUD APIs: the only way to reach it is the guarded orchestration step.
 */
@Service
public class SystemResourceBuilder {
    public static final String AGENT_ID = "__system_resource_builder__";
    private static final int MAX_AUTO_BOUND_SKILLS = 5;
    private static final int MAX_AUTO_BOUND_CAPABILITIES = 12;
    private static final AgentCapabilitySummary CANDIDATE = new AgentCapabilitySummary(
            AGENT_ID, 1L, "系统资源构建器",
            "仅用于创建当前用户自己的 Agent 或 Team；按最小权限读取可用 Skill/MCP 并完成绑定。"
    );

    private final SkillDefinitionService skillService;
    private final McpServerService mcpServerService;
    private final AgentDefinitionService agentService;
    private final AgentTeamService teamService;
    private final AgentPromptCompiler promptCompiler;
    private final ObjectMapper objectMapper;
    private final OrchestrationModelPort modelPort;

    @Autowired
    public SystemResourceBuilder(
            SkillDefinitionService skillService,
            McpServerService mcpServerService,
            AgentDefinitionService agentService,
            AgentTeamService teamService,
            AgentPromptCompiler promptCompiler,
            ObjectMapper objectMapper,
            OrchestrationModelPort modelPort
    ) {
        this.skillService = skillService;
        this.mcpServerService = mcpServerService;
        this.agentService = agentService;
        this.teamService = teamService;
        this.promptCompiler = promptCompiler;
        this.objectMapper = objectMapper;
        this.modelPort = modelPort;
    }

    SystemResourceBuilder(
            SkillDefinitionService skillService,
            McpServerService mcpServerService,
            AgentDefinitionService agentService,
            AgentTeamService teamService,
            AgentPromptCompiler promptCompiler
    ) {
        this(skillService, mcpServerService, agentService, teamService, promptCompiler, new ObjectMapper(), null);
    }

    public static boolean requiresResourceCreation(String query) {
        String text = stripNegatedCreationClauses(normalize(ChatAttachmentPrompt.withoutUploadedFileBodies(query)));
        if (text.isBlank()) return false;
        if (containsAny(text, "现有团队", "当前团队", "existing team", "current team")) return false;
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
        return create(user, query, "");
    }

    /** Creates resources from the original request plus the hidden Agent's planned definition. */
    public String create(CurrentUser user, String query, String plannedDefinition) {
        Available available = availableFor(user);
        String blueprint = modelPort == null ? "" : modelPort.designResourceTeam(query, capabilityCatalog(available));
        Optional<Request> designed = parseBlueprint(blueprint, query)
                .or(() -> parseBlueprint(plannedDefinition, query));
        if (modelPort != null && designed.isEmpty()) {
            return "资源未创建：系统资源构建器未生成有效的角色型 Team 蓝图，请重试。";
        }
        Request request = designed.orElseGet(() -> Request.from(query));
        if (request.team()) {
            return createTeam(user, request, available);
        }
        Optional<AgentResponse> reusable = findReusable(available.agents(), request.roles().get(0), new LinkedHashSet<>());
        AgentResponse agent = reusable.orElseGet(() -> createOnlineAgent(user, request.roles().get(0), available, query));
        return "资源已" + (reusable.isPresent() ? "复用" : "创建") + "：Agent「" + agent.name() + "」（id=" + agent.id() + "），已绑定 "
                + agent.skillIds().size() + " 个 Skill 和 " + agent.capabilityKeys().size() + " 个 MCP 工具。当前对话不会自动切换 Agent。";
    }

    private String capabilityCatalog(Available available) {
        StringBuilder catalog = new StringBuilder("enabledSkills:\n");
        for (SkillResponse skill : available.skills()) {
            catalog.append("- ").append(skill.name()).append(": ").append(nullToEmpty(skill.description())).append('\n');
        }
        catalog.append("availableCapabilities:\n");
        for (CapabilityOption capability : available.capabilities()) {
            catalog.append("- ").append(capability.name()).append(": ")
                    .append(nullToEmpty(capability.description())).append('\n');
        }
        catalog.append("existingOnlineAgents:\n");
        for (AgentResponse agent : available.agents()) {
            catalog.append("- ").append(agent.name()).append(": ").append(nullToEmpty(agent.description())).append('\n');
        }
        return catalog.toString();
    }

    /**
     * The hidden system Agent supplies this compact JSON blueprint in its plan objective.
     * Reject malformed or over-broad data and fall back to the legacy safe template.
     */
    private Optional<Request> parseBlueprint(String text, String rawQuery) {
        if (text == null || text.isBlank() || !text.trim().startsWith("{")) return Optional.empty();
        try {
            JsonNode root = objectMapper.readTree(text);
            if (root == null || !root.isObject() || !root.has("team") || !root.has("agents")) {
                return Optional.empty();
            }
            if (!root.path("team").isBoolean() || !root.path("agents").isArray()) return Optional.empty();
            boolean team = root.path("team").asBoolean();
            if (team != (root.size() == 4 && root.has("teamName") && root.has("teamDescription"))
                    || (!team && root.size() != 2)) return Optional.empty();
            String name = team ? requiredText(root, "teamName", 80) : "";
            String description = team ? requiredText(root, "teamDescription", 500) : "";
            List<RoleSpec> roles = new ArrayList<>();
            for (JsonNode agent : root.path("agents")) {
                if (!agent.isObject() || agent.size() != 4) return Optional.empty();
                String agentName = requiredText(agent, "name", 80);
                if (!isProfessionalRoleTitle(agentName)) return Optional.empty();
                String agentDescription = requiredText(agent, "description", 500);
                String prompt = requiredText(agent, "systemPrompt", 8000);
                String skillHint = requiredText(agent, "capabilityHints", 500);
                roles.add(new RoleSpec(agentName, agentDescription, prompt, skillHint));
            }
            if ((team && (roles.size() < 2 || roles.size() > 20)) || (!team && roles.size() != 1)) return Optional.empty();
            if (!team) {
                RoleSpec agent = roles.get(0);
                return Optional.of(new Request(false, agent.name(), agent.description(), List.of(agent), rawQuery));
            }
            return Optional.of(new Request(true, name, description, List.copyOf(roles), rawQuery));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private static String requiredText(JsonNode node, String field, int maxLength) {
        JsonNode value = node.path(field);
        if (!value.isTextual()) throw new IllegalArgumentException("missing " + field);
        String text = value.asText().trim();
        if (text.isBlank() || text.length() > maxLength) throw new IllegalArgumentException("invalid " + field);
        return text;
    }

    /** Prevent a model's work-package label from becoming a user-visible Agent name. */
    private static boolean isProfessionalRoleTitle(String name) {
        String normalized = name.replaceAll("\\s+", "");
        if (normalized.matches(".*(设计|治理|渲染|归档|验证|量化|执行|监控|分析|建模|开发|补全)$")) return false;
        return normalized.matches(".*(负责人|总监|科学家|研究员|专家|工程师|分析师|架构师|顾问|审校)$")
                || normalized.matches("(?i).*(lead|director|scientist|researcher|specialist|engineer|analyst|architect|advisor|reviewer)$");
    }

    private String createTeam(CurrentUser user, Request request, Available available) {
        List<AgentResponse> agents = new ArrayList<>();
        int reused = 0;
        LinkedHashSet<String> claimedAgentIds = new LinkedHashSet<>();
        for (RoleSpec role : request.roles()) {
            Optional<AgentResponse> existing = findReusable(available.agents(), role, claimedAgentIds);
            if (existing.isPresent()) {
                agents.add(existing.get());
                claimedAgentIds.add(existing.get().id());
                reused++;
            } else {
                agents.add(createOnlineAgent(user, role, available, request.rawQuery()));
            }
        }
        TeamResponse team = teamService.createTeam(user, new TeamCreateRequest(
                teamService.nextAvailableName(user, request.name()), request.description(), agents.get(0).id(),
                agents.subList(1, agents.size()).stream().map(AgentResponse::id).toList()
        ));
        int skills = agents.stream().mapToInt(agent -> agent.skillIds().size()).sum();
        int mcp = agents.stream().mapToInt(agent -> agent.capabilityKeys().size()).sum();
        return "资源已创建：Team「" + team.name() + "」（id=" + team.id() + "），主 Agent 为「"
                + agents.get(0).name() + "」，共 " + agents.size() + " 个 Agent；按最小权限共绑定 "
                + skills + " 个 Skill 和 " + mcp + " 个 MCP 工具；复用 " + reused + " 个既有 Agent，新建 "
                + (agents.size() - reused) + " 个。当前对话仍使用原 Team；如需使用新 Team，请在后续会话手动切换。";
    }

    private AgentResponse createOnlineAgent(
            CurrentUser user, RoleSpec role, Available available, String query
    ) {
        List<SkillResponse> skills = selectSkills(available.skills(), role, query);
        List<String> capabilities = selectCapabilities(available.capabilities(), role.skillHint(), query);
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
        PageResponse<AgentResponse> agents = agentService.listAgents(user, 1, 100);
        List<AgentResponse> onlineAgents = agents == null || agents.items() == null ? List.of() : agents.items().stream()
                .filter(agent -> "ONLINE".equals(agent.status()))
                .toList();
        List<CapabilityOption> capabilities = new ArrayList<>(List.of(
                new CapabilityOption(CapabilityKeys.BUILTIN_DEEP_SEARCH, CapabilityKeys.BUILTIN_DEEP_SEARCH,
                        "联网搜索网页、检索公开信息并整理来源"),
                new CapabilityOption(CapabilityKeys.BUILTIN_CODE_INTERPRETER, CapabilityKeys.BUILTIN_CODE_INTERPRETER,
                        "在浏览器 Python 环境运行代码并读写工作区文件"),
                new CapabilityOption(CapabilityKeys.BUILTIN_DATA_ANALYSIS, CapabilityKeys.BUILTIN_DATA_ANALYSIS,
                        "分析 CSV、Excel 等结构化数据"),
                new CapabilityOption(CapabilityKeys.BUILTIN_FILE, CapabilityKeys.BUILTIN_FILE,
                        "读取、写入和管理工作区文件"),
                new CapabilityOption(CapabilityKeys.BUILTIN_REPORT, CapabilityKeys.BUILTIN_REPORT,
                        "生成报告和可下载交付文件")
        ));
        for (McpToolResponse tool : mcpServerService.capabilities(user)) {
            capabilities.add(new CapabilityOption(
                    CapabilityKeys.forMcpTool(tool.id()),
                    tool.runtimeName(),
                    tool.description()
            ));
        }
        return new Available(skills, List.copyOf(capabilities), onlineAgents);
    }

    private Optional<AgentResponse> findReusable(List<AgentResponse> candidates, RoleSpec role, LinkedHashSet<String> claimedAgentIds) {
        if (candidates == null) return Optional.empty();
        return candidates.stream()
                .filter(agent -> agent.id() != null && !claimedAgentIds.contains(agent.id()))
                .filter(agent -> role.matchesExisting(agent))
                .findFirst();
    }

    /**
     * Agent prompts embed each selected Skill instruction.  A marketplace Skill can be
     * perfectly valid on its own yet be too large in combination with other Skills.
     * Build the selection incrementally with the same compiler used by Agent creation,
     * so an oversized or legacy-invalid package never aborts Team creation.
     */
    private List<SkillResponse> selectSkills(List<SkillResponse> candidates, RoleSpec role, String query) {
        List<SkillResponse> selected = new ArrayList<>();
        for (SkillResponse candidate : candidates) {
            if (explicitlyRequests(role.skillHint(), candidate.name()) && isCompilable(role.systemPrompt(), List.of(candidate))) {
                selected.add(candidate);
            }
            if (selected.size() == MAX_AUTO_BOUND_SKILLS) return List.copyOf(selected);
        }
        if (!selected.isEmpty()) return List.copyOf(selected);
        String selectionText = role.skillHint().isBlank() ? query : role.skillHint();
        for (SkillResponse candidate : candidates) {
            if (!matches(selectionText,
                    candidate.name() + " " + candidate.description() + " " + candidate.instruction())) {
                continue;
            }
            List<SkillResponse> proposed = new ArrayList<>(selected);
            proposed.add(candidate);
            if (isCompilable(role.systemPrompt(), proposed)) {
                selected.add(candidate);
            }
            if (selected.size() == MAX_AUTO_BOUND_SKILLS) break;
        }
        return List.copyOf(selected);
    }

    private boolean isCompilable(String systemPrompt, List<SkillResponse> skills) {
        List<PromptSkillFragment> fragments = new ArrayList<>();
        for (int index = 0; index < skills.size(); index++) {
            SkillResponse skill = skills.get(index);
            fragments.add(new PromptSkillFragment(skill.id(), skill.version(), skill.name(), index + 1,
                    skill.instruction(), skill.outputRequirement()));
        }
        try {
            promptCompiler.compile(new PromptCompilationRequest("RAW", null, systemPrompt, fragments));
            return true;
        } catch (PromptValidationException ex) {
            return false;
        }
    }

    private List<String> selectCapabilities(List<CapabilityOption> candidates, String skillHint, String query) {
        LinkedHashSet<String> selected = new LinkedHashSet<>();
        for (CapabilityOption capability : candidates) {
            if (explicitlyRequests(skillHint, capability.name())) {
                selected.add(capability.key());
            }
            if (selected.size() == MAX_AUTO_BOUND_CAPABILITIES) return List.copyOf(selected);
        }
        if (!selected.isEmpty()) return List.copyOf(selected);
        String selectionText = skillHint == null || skillHint.isBlank() ? query : skillHint;
        for (CapabilityOption capability : candidates) {
            if (matches(selectionText, capability.name() + " " + nullToEmpty(capability.description()))) {
                selected.add(capability.key());
            }
            if (selected.size() == MAX_AUTO_BOUND_CAPABILITIES) break;
        }
        return List.copyOf(selected);
    }

    private static boolean explicitlyRequests(String hints, String resourceName) {
        String left = normalize(hints);
        String right = normalize(resourceName);
        return !right.isBlank() && left.contains(right);
    }

    private static boolean matches(String request, String resource) {
        String left = normalize(request);
        String right = normalize(resource);
        List<List<String>> domains = List.of(
                List.of("python", "代码解释器", "脚本", "运行代码", "code interpreter"),
                List.of("数据", "csv", "excel", "分析", "统计", "图表", "data", "analytics"),
                List.of("pdf", "文档", "文件", "报告", "file", "document", "report"),
                List.of("网页", "联网", "搜索", "检索", "调研", "web", "search", "research"),
                List.of("地图", "高德", "poi", "地理", "经纬度", "位置", "定位", "路线", "导航", "驾车", "步行", "公交", "距离", "天气", "旅游", "景点", "maps", "geo", "location", "route", "direction", "distance", "weather"),
                List.of("前端", "后端", "代码", "开发", "github", "api", "frontend", "backend", "code"),
                List.of("测试", "质量", "验收", "qa", "test"),
                List.of("紫微", "命盘", "运势", "星盘", "ziwei")
        );
        for (List<String> domain : domains) {
            if (containsAny(left, domain) && containsAny(right, domain)) return true;
        }
        return false;
    }

    private static boolean containsAny(String value, List<String> values) {
        for (String item : values) if (value.contains(item)) return true;
        return false;
    }

    private static boolean containsAny(String value, String... values) {
        for (String item : values) if (value.contains(item)) return true;
        return false;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }

    private static String stripNegatedCreationClauses(String value) {
        String withoutChineseNegation = Pattern.compile(
                "(?:不要|无需|不用|不必|不需要|别|禁止|不可|不能|不是(?:让|要)?)\\s*(?:再)?\\s*" +
                        "(?:创建|新建|生成|组建|搭建)[^，。；;,.!?]*",
                Pattern.CASE_INSENSITIVE
        ).matcher(value).replaceAll(" ");
        return Pattern.compile(
                "(?:do\\s+not|don't|dont|no\\s+need\\s+to|without)\\s+(?:create|build|generate)\\b[^,.;!?]*",
                Pattern.CASE_INSENSITIVE
        ).matcher(withoutChineseNegation).replaceAll(" ").trim();
    }

    private static String nullToEmpty(String value) { return value == null ? "" : value; }

    private record Available(List<SkillResponse> skills, List<CapabilityOption> capabilities, List<AgentResponse> agents) { }

    private record CapabilityOption(String key, String name, String description) { }

    private record RoleSpec(String name, String description, String systemPrompt, String skillHint, List<String> reuseHints) {
        RoleSpec(String name, String description, String systemPrompt, String skillHint) {
            this(name, description, systemPrompt, skillHint, List.of(name));
        }

        boolean matchesExisting(AgentResponse agent) {
            String text = normalize(agent.name() + " " + agent.description() + " " + agent.systemPrompt());
            return effectiveReuseHints().stream().map(SystemResourceBuilder::normalize)
                    .anyMatch(hint -> !hint.isBlank() && text.contains(hint));
        }

        private List<String> effectiveReuseHints() {
            return switch (name) {
                case "技术负责人" -> List.of("技术负责人", "tech lead", "架构师", "技术主管");
                case "后端开发" -> List.of("后端开发", "后端工程师", "backend", "服务端");
                case "前端开发" -> List.of("前端开发", "前端工程师", "frontend", "ui 开发");
                case "测试工程师" -> List.of("测试工程师", "测试开发", "qa", "quality assurance");
                case "数据分析师" -> List.of("数据分析师", "数据分析", "data analyst");
                case "分析负责人" -> List.of("分析负责人", "数据负责人", "分析师");
                case "结果复核" -> List.of("结果复核", "审校", "reviewer", "质量复核");
                default -> reuseHints;
            };
        }
    }

    private record Request(boolean team, String name, String description, List<RoleSpec> roles, String rawQuery) {
        private static final Pattern ARABIC_COUNT = Pattern.compile(
                "(?<!\\d)(20|1\\d|[2-9])\\s*(?:(?:个|名|位)\\s*(?:agents?|智能体|代理|成员|人)?|人|agents?|智能体|代理|成员)(?!\\d)",
                Pattern.CASE_INSENSITIVE
        );
        private static final Pattern NAMED_RESOURCE = Pattern.compile(
                "(?:名为|名称(?:是|为)?|叫(?:做|作)?|named)\\s*[“\\\"']([^”\\\"'\\r\\n]{1,80})[”\\\"']",
                Pattern.CASE_INSENSITIVE
        );
        private static final Pattern QUOTED_TEAM = Pattern.compile(
                "(?:创建|新建|组建|搭建)\\s*(?:一个|一支|1\\s*个)?\\s*[“\\\"']([^”\\\"'\\r\\n]{1,80})[”\\\"']\\s*(?:的)?(?:团队|小组|team)",
                Pattern.CASE_INSENSITIVE
        );

        static Request from(String query) {
            String text = normalize(query);
            boolean team = containsAny(text, "团队", "小组", " team", "team ");
            Request request;
            if (containsAny(text, "软件", "开发", "code", "代码", "web", "前端", "后端")) {
                request = software(team, query);
            } else if (containsAny(text, "数据", "csv", "excel", "分析", "报表")) {
                request = data(team, query);
            } else if (containsAny(text, "研究", "调研", "竞品", "财报")) {
                request = research(team, query);
            } else if (containsAny(text, "语义通信", "频谱地图", "频谱补全", "无线通信", "频谱感知")) {
                request = semanticSpectrum(team, query);
            } else {
                request = general(team, query);
            }
            return withRequestedName(withRequestedCount(request, requestedCount(text)), query);
        }

        private static Request withRequestedName(Request request, String query) {
            if (!request.team()) return request;
            String requestedName = requestedName(query);
            if (requestedName == null) return request;
            return new Request(true, requestedName, request.description(), request.roles(), request.rawQuery());
        }

        private static String requestedName(String query) {
            for (Pattern pattern : List.of(NAMED_RESOURCE, QUOTED_TEAM)) {
                Matcher matcher = pattern.matcher(query == null ? "" : query);
                if (matcher.find()) {
                    String name = matcher.group(1).trim();
                    if (!name.isBlank()) return name;
                }
            }
            return null;
        }

        private static Request withRequestedCount(Request request, Integer requestedCount) {
            if (!request.team() || requestedCount == null) return request;
            List<RoleSpec> roles = new ArrayList<>(request.roles());
            if (requestedCount < roles.size()) {
                roles = new ArrayList<>(roles.subList(0, requestedCount));
            }
            for (int position = roles.size() + 1; position <= requestedCount; position++) {
                roles.add(new RoleSpec(
                        request.name().replace("团队", "") + "专员 " + position,
                        "协助完成「" + request.name() + "」中的具体工作。",
                        "你是团队中的执行专员。依据团队目标完成分配任务，明确说明依据、风险和未完成项；只使用当前已绑定的 Skill 和工具。",
                        "file web api"
                ));
            }
            return new Request(true, request.name(), request.description(), List.copyOf(roles), request.rawQuery());
        }

        private static Integer requestedCount(String text) {
            Matcher matcher = ARABIC_COUNT.matcher(text);
            while (matcher.find()) {
                int value = Integer.parseInt(matcher.group(1));
                if (value >= 2 && value <= 20) return value;
            }
            for (String word : List.of("二十", "十九", "十八", "十七", "十六", "十五", "十四", "十三", "十二", "十一", "十", "九", "八", "七", "六", "五", "四", "三", "两", "二")) {
                if (text.contains(word + "个") || text.contains(word + "名") || text.contains(word + "位") || text.contains(word + "人")) {
                    return switch (word) {
                        case "二十" -> 20; case "十九" -> 19; case "十八" -> 18; case "十七" -> 17; case "十六" -> 16;
                        case "十五" -> 15; case "十四" -> 14; case "十三" -> 13; case "十二" -> 12; case "十一" -> 11;
                        case "十" -> 10; case "九" -> 9; case "八" -> 8; case "七" -> 7; case "六" -> 6;
                        case "五" -> 5; case "四" -> 4; case "三" -> 3; default -> 2;
                    };
                }
            }
            return null;
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

        private static Request semanticSpectrum(boolean team, String query) {
            RoleSpec lead = new RoleSpec("语义通信项目负责人", "制定语义通信与频谱地图补全总体方案，拆解研究任务并统筹验收。", """
                    你是语义通信与频谱地图补全项目负责人。先定义研究问题、数据假设、评价指标和交付物，再协调各角色并汇总结果。
                    明确区分理论推导、实验事实和待验证假设；检查各模块接口与实验口径一致。只使用当前已绑定的 Skill 和工具。
                    """.strip(), "语义通信 频谱 地图 补全 项目 管理");
            RoleSpec semantic = new RoleSpec("语义通信算法研究员", "研究语义信息提取、语义编码传输和任务相关性度量，降低频谱数据通信开销。", """
                    你负责语义通信算法。围绕频谱感知任务设计语义表示、编码传输、失真度量和抗噪机制，给出可复现的公式、假设与基线。
                    说明语义压缩相对原始 IQ/功率谱数据的通信开销、性能损失和适用边界，不要编造实验结果。
                    """.strip(), "语义通信 信息论 编码 传输 抗噪");
            RoleSpec completion = new RoleSpec("频谱地图补全算法专家", "设计稀疏采样频谱地图的时空补全模型与对比实验。", """
                    你负责频谱地图补全。分析空间拓扑、时间相关性和缺失机制，比较图神经网络、张量分解、时空模型和生成式方法。
                    明确输入输出张量、训练/验证划分、缺失率、指标和基线，避免数据泄漏；所有结论必须绑定实验或文献依据。
                    """.strip(), "频谱地图 补全 时空 图神经网络 张量 生成式");
            RoleSpec sensing = new RoleSpec("无线信道与频谱感知研究员", "负责信道建模、电磁环境、频谱采样策略和实测数据质量。", """
                    你负责无线信道与频谱感知。建立传播、干扰、噪声和占用状态假设，设计采样与标注方案，检查仿真数据和外场数据的一致性。
                    对模型适用频段、带宽、采样率和信噪比给出明确说明，区分测量事实与建模假设。
                    """.strip(), "无线 信道 频谱感知 电磁 采样 信噪比");
            RoleSpec ml = new RoleSpec("AI模型与系统优化工程师", "负责模型训练调优、压缩加速和边缘部署。", """
                    你负责 AI 模型工程化。制定训练配置、消融实验、复杂度和显存评估，进行剪枝、量化或蒸馏，并说明边缘部署约束。
                    给出可执行的复现实验步骤和失败诊断，不把未经运行的代码或指标当成已验证结果。
                    """.strip(), "深度学习 训练 优化 轻量化 边缘部署");
            RoleSpec validation = new RoleSpec("仿真平台与实验验证工程师", "搭建仿真和数据处理流水线，执行对比实验并复核统计结果。", """
                    你负责实验验证。搭建可重复的仿真、数据预处理和评测流水线，统一随机种子、数据划分和指标计算。
                    对比语义通信与传统传输方案，报告误差、通信开销、运行时间和置信区间；工具不可用时明确记录阻塞。
                    """.strip(), "仿真 实验 验证 数据处理 指标 对比");
            List<RoleSpec> roles = List.of(lead, semantic, completion, sensing, ml, validation);
            if (!team) return new Request(false, lead.name(), lead.description(), roles.subList(0, 1), query);
            return new Request(true, "语义通信频谱地图补全科研团队", "围绕语义通信与稀疏频谱地图补全开展理论、算法、系统和实验协作。", roles, query);
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
