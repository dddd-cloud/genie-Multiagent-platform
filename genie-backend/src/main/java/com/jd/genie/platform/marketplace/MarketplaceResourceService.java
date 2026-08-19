package com.jd.genie.platform.marketplace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2.configuration.agent.dto.AgentCreateRequest;
import com.jd.genie.platform.phase2.configuration.agent.dto.AgentResponse;
import com.jd.genie.platform.phase2.configuration.agent.dto.AgentSkillBindingRequest;
import com.jd.genie.platform.phase2.configuration.agent.service.AgentDefinitionService;
import com.jd.genie.platform.phase2.configuration.model.ModelCatalogService;
import com.jd.genie.platform.phase2.configuration.skill.dto.SkillResponse;
import com.jd.genie.platform.phase2.configuration.skill.service.SkillPackageImportService;
import com.jd.genie.platform.phase2.configuration.team.dto.TeamCreateRequest;
import com.jd.genie.platform.phase2.configuration.team.dto.TeamResponse;
import com.jd.genie.platform.phase2.configuration.team.service.AgentTeamService;
import com.jd.genie.platform.phase2.tooling.AuthType;
import com.jd.genie.platform.phase2.tooling.CreateMcpServerRequest;
import com.jd.genie.platform.phase2.tooling.McpServerResponse;
import com.jd.genie.platform.phase2.tooling.McpServerService;
import com.jd.genie.platform.phase2.tooling.McpServerStatus;
import com.jd.genie.platform.phase2.tooling.McpToolResponse;
import com.jd.genie.platform.phase2.tooling.UpdateToolEnabledRequest;
import com.jd.genie.platform.phase2contract.error.Phase2ContractException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.stream.IntStream;

@Slf4j
@Service
public class MarketplaceResourceService {
    private final ObjectMapper objectMapper;
    private final List<MarketplaceCatalogEntry> catalog;
    private final SkillPackageImportService skillPackageImportService;
    private final AgentDefinitionService agentDefinitionService;
    private final AgentTeamService agentTeamService;
    private final MarketplacePackageArchiveService packageArchiveService;
    private final McpServerService mcpServerService;

    @Autowired
    public MarketplaceResourceService(
        ObjectMapper objectMapper,
        SkillPackageImportService skillPackageImportService,
        AgentDefinitionService agentDefinitionService,
        AgentTeamService agentTeamService,
        MarketplacePackageArchiveService packageArchiveService,
        McpServerService mcpServerService
    ) {
        this.objectMapper = objectMapper;
        this.skillPackageImportService = skillPackageImportService;
        this.agentDefinitionService = agentDefinitionService;
        this.agentTeamService = agentTeamService;
        this.packageArchiveService = packageArchiveService;
        this.mcpServerService = mcpServerService;
        this.catalog = loadCatalog();
    }

    /** Backward-compatible constructor used by focused marketplace tests. */
    public MarketplaceResourceService(
        ObjectMapper objectMapper,
        SkillPackageImportService skillPackageImportService,
        AgentDefinitionService agentDefinitionService,
        AgentTeamService agentTeamService,
        MarketplacePackageArchiveService packageArchiveService
    ) {
        this(objectMapper, skillPackageImportService, agentDefinitionService, agentTeamService,
            packageArchiveService, null);
    }

    /** Public test seam for catalog-only behavior; no installation services are available. */
    public MarketplaceResourceService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.skillPackageImportService = null;
        this.agentDefinitionService = null;
        this.agentTeamService = null;
        this.packageArchiveService = null;
        this.mcpServerService = null;
        this.catalog = loadCatalog();
    }

    /** Test seam: parse a catalog document without touching the classpath file. */
    MarketplaceResourceService(ObjectMapper objectMapper, String catalogJson) {
        this.objectMapper = objectMapper;
        this.skillPackageImportService = null;
        this.agentDefinitionService = null;
        this.agentTeamService = null;
        this.packageArchiveService = null;
        this.mcpServerService = null;
        this.catalog = parseCatalogDocument(catalogJson);
    }

    /**
     * Installs a reviewed, bundled resource through existing Phase2 public services.
     * It deliberately never writes Phase2 mapper/entity objects itself.
     */
    @Transactional
    public MarketplaceInstallResponse install(CurrentUser user, String id) {
        requireUser(user);
        MarketplaceCatalogEntry entry = find(id).orElseThrow(() -> new MarketplaceNotFoundException(id));
        return switch (entry.type()) {
            case SKILL -> installSkill(user, entry);
            case AGENT -> installAgent(user, entry);
            case TEAM -> installTeam(user, entry);
            case MCP -> installMcp(entry);
        };
    }

    public List<MarketplaceResourceView> search(
        MarketplaceResourceType type,
        String category,
        String query
    ) {
        String normalizedCategory = normalize(category);
        String normalizedQuery = normalize(query);
        return catalog.stream()
            .filter(entry -> type == null || entry.type() == type)
            .filter(entry -> normalizedCategory.isBlank() || normalizedCategory.equals(normalize(entry.category())))
            .filter(entry -> normalizedQuery.isBlank() || matches(entry, normalizedQuery))
            .sorted(Comparator.comparing(MarketplaceCatalogEntry::type).thenComparing(MarketplaceCatalogEntry::name))
            .map(MarketplaceResourceView::from)
            .toList();
    }

    public List<String> categories() {
        return catalog.stream()
            .map(MarketplaceCatalogEntry::category)
            .distinct()
            .sorted()
            .toList();
    }

    public MarketplaceResourceView get(String id) {
        return find(id).map(MarketplaceResourceView::from)
            .orElseThrow(() -> new MarketplaceNotFoundException(id));
    }

    public MarketplaceDraftResponse createDraft(CurrentUser user, String id) {
        MarketplaceCatalogEntry entry = find(id)
            .orElseThrow(() -> new MarketplaceNotFoundException(id));
        JsonNode draft = entry.draft() == null ? objectMapper.createObjectNode() : entry.draft().deepCopy();
        List<String> missing = missingFields(entry.type(), draft);
        return new MarketplaceDraftResponse(
            entry.id(),
            entry.type(),
            entry.name(),
            user == null ? null : user.userId(),
            draft,
            List.of("这是当前用户的草稿预览，确认后仍需通过现有资源管理接口保存。", "广场模板不包含 Credential、Token、Cookie 或租户标识。"),
            missing.isEmpty() ? "READY" : "NEEDS_CONFIGURATION",
            List.copyOf(missing)
        );
    }

    public List<MarketplaceCatalogEntry> entries() {
        return catalog;
    }

    private java.util.Optional<MarketplaceCatalogEntry> find(String id) {
        return catalog.stream().filter(entry -> entry.id().equals(id)).findFirst();
    }

    private boolean matches(MarketplaceCatalogEntry entry, String query) {
        String haystack = String.join(" ", entry.name(), entry.tagline(), entry.description(), entry.category(), String.join(" ", entry.tags())).toLowerCase(Locale.ROOT);
        return haystack.contains(query);
    }

    private List<MarketplaceCatalogEntry> loadCatalog() {
        try (InputStream input = new ClassPathResource("marketplace/catalog.json").getInputStream()) {
            return parseCatalogDocument(new String(input.readAllBytes()));
        } catch (IOException exception) {
            log.error("Unable to load marketplace catalog; serving an empty directory", exception);
            return List.of();
        }
    }

    private List<MarketplaceCatalogEntry> parseCatalogDocument(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root == null || !root.isArray()) {
                log.error("Marketplace catalog is not a JSON array; serving an empty directory");
                return List.of();
            }
            List<MarketplaceCatalogEntry> accepted = new ArrayList<>();
            Set<String> ids = new HashSet<>();
            Set<String> slugs = new HashSet<>();
            int index = 0;
            for (JsonNode node : root) {
                index++;
                try {
                    MarketplaceCatalogEntry entry = objectMapper.treeToValue(node, MarketplaceCatalogEntry.class);
                    String reason = invalidReason(entry, ids, slugs);
                    if (reason != null) {
                        log.error("Skipping marketplace catalog entry #{} id={}: {}", index, node.path("id").asText(), reason);
                        continue;
                    }
                    ids.add(entry.id());
                    slugs.add(entry.slug());
                    accepted.add(entry);
                } catch (Exception exception) {
                    log.error("Skipping malformed marketplace catalog entry #{}", index, exception);
                }
            }
            return List.copyOf(accepted);
        } catch (Exception exception) {
            log.error("Marketplace catalog failed to parse; serving an empty directory", exception);
            return List.of();
        }
    }

    private String invalidReason(MarketplaceCatalogEntry entry, Set<String> ids, Set<String> slugs) {
        if (entry == null || blank(entry.id()) || blank(entry.slug()) || blank(entry.name()) || blank(entry.description())
            || blank(entry.category()) || entry.type() == null) {
            return "missing required metadata";
        }
        if (ids.contains(entry.id()) || slugs.contains(entry.slug())) {
            return "duplicate id or slug";
        }
        if (containsSensitive(entry.draft())) {
            return "draft contains sensitive keys";
        }
        if (entry.type() == MarketplaceResourceType.AGENT) {
            JsonNode draft = entry.draft();
            if (draft == null || !draft.hasNonNull("systemPrompt") || draft.get("systemPrompt").asText().isBlank()
                || !Set.of("RAW", "STRUCTURED").contains(draft.path("promptMode").asText())) {
                return "invalid Agent draft contract";
            }
        }
        if (entry.type() == MarketplaceResourceType.SKILL && (entry.delivery() == null
            || !"EMBEDDED_SKILL_PACKAGE".equals(entry.delivery().path("mode").asText()))) {
            return "Skill entry must have an embedded reviewed package";
        }
        return null;
    }

    private MarketplaceInstallResponse installSkill(CurrentUser user, MarketplaceCatalogEntry entry) {
        requireInstallServices();
        SkillResponse skill = skillPackageImportService.importPackage(user, packageArchiveService.archive(entry.delivery()), null);
        return new MarketplaceInstallResponse(entry.id(), entry.type(), skill.id(), List.of(), List.of(skill.id()),
            null, "INSTALLED", "ENABLED".equals(skill.status()), List.of());
    }

    private MarketplaceInstallResponse installAgent(CurrentUser user, MarketplaceCatalogEntry entry) {
        requireInstallServices();
        InstalledAgent installed = createOnlineAgent(user, entry, entry.name(), new LinkedHashMap<>());
        return new MarketplaceInstallResponse(entry.id(), entry.type(), installed.agent().id(), List.of(installed.agent().id()),
            installed.skillIds(), null, "INSTALLED", "ONLINE".equals(installed.agent().status()), List.of());
    }

    private MarketplaceInstallResponse installTeam(CurrentUser user, MarketplaceCatalogEntry entry) {
        requireInstallServices();
        List<String> templateIds = stringList(entry.draft().path("recommendedAgentTemplates"));
        if (templateIds.isEmpty()) throw invalid("Team template has no Agent blueprints");
        List<InstalledAgent> installed = new ArrayList<>();
        Map<String, String> installedSkillIds = new LinkedHashMap<>();
        for (int i = 0; i < templateIds.size(); i++) {
            MarketplaceCatalogEntry blueprint = find(templateIds.get(i)).orElseThrow(() -> invalid("Team Agent blueprint is missing"));
            if (blueprint.type() != MarketplaceResourceType.AGENT) throw invalid("Team blueprint must reference Agent templates");
            installed.add(createOnlineAgent(user, blueprint, entry.name() + " · " + blueprint.name(), installedSkillIds));
        }
        if (installed.size() == 1) {
            MarketplaceCatalogEntry blueprint = find(templateIds.get(0)).orElseThrow(() -> invalid("Team Agent blueprint is missing"));
            installed.add(createOnlineAgent(user, blueprint, entry.name() + " · 结果复核", installedSkillIds));
        }
        String teamName = agentTeamService.nextAvailableName(user, entry.name());
        TeamResponse team = agentTeamService.createTeam(user, new TeamCreateRequest(teamName, entry.description(),
            installed.get(0).agent().id(), installed.subList(1, installed.size()).stream().map(item -> item.agent().id()).toList()));
        List<String> agents = installed.stream().map(item -> item.agent().id()).toList();
        List<String> skills = installed.stream().flatMap(item -> item.skillIds().stream()).distinct().toList();
        return new MarketplaceInstallResponse(entry.id(), entry.type(), team.id(), agents, skills, team.id(),
            "INSTALLED", true, List.of());
    }

    private InstalledAgent createOnlineAgent(CurrentUser user, MarketplaceCatalogEntry entry, String name,
                                             Map<String, String> installedSkillIds) {
        List<String> installedSkills = installLinkedSkills(user, entry.draft(), installedSkillIds);
        List<AgentSkillBindingRequest> bindings = IntStream.range(0, installedSkills.size())
            .mapToObj(index -> new AgentSkillBindingRequest(installedSkills.get(index), index + 1)).toList();
        AgentResponse created = agentDefinitionService.createAgent(user, new AgentCreateRequest(
            agentDefinitionService.nextAvailableName(user, name),
            entry.draft().path("description").asText(entry.description()),
            entry.draft().path("promptMode").asText("RAW"),
            entry.draft().path("promptConfig").isMissingNode() ? null : entry.draft().path("promptConfig").asText(null),
            entry.draft().path("systemPrompt").asText(),
            catalogModelName(entry.draft()),
            bindings,
            stringList(entry.draft().path("capabilityKeys"))
        ));
        return new InstalledAgent(agentDefinitionService.onlineAgent(user, created.id(), created.version()), installedSkills);
    }

    private List<String> installLinkedSkills(CurrentUser user, JsonNode draft, Map<String, String> installedSkillIds) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (String linkedId : stringList(draft.path("marketplaceSkillIds"))) {
            MarketplaceCatalogEntry skillEntry = find(linkedId).orElseThrow(() -> invalid("linked Skill template is missing"));
            if (skillEntry.type() != MarketplaceResourceType.SKILL) throw invalid("linked resource is not a Skill");
            String installedId = installedSkillIds.get(linkedId);
            if (installedId == null) {
                installedId = installSkill(user, skillEntry).primaryResourceId();
                installedSkillIds.put(linkedId, installedId);
            }
            ids.add(installedId);
        }
        return List.copyOf(ids);
    }

    private MarketplaceInstallResponse installMcp(MarketplaceCatalogEntry entry) {
        if (mcpServerService == null) {
            throw new IllegalStateException("Marketplace MCP install service is unavailable in this test seam");
        }
        JsonNode draft = entry.draft();
        String serverUrl = draft == null ? "" : draft.path("serverUrl").asText("").trim();
        String authType = draft == null ? "" : draft.path("authType").asText("").trim();
        String transportType = draft == null ? "" : draft.path("transportType").asText("").trim();
        Set<String> allowedTools = new LinkedHashSet<>(stringList(draft == null ? null : draft.path("allowedTools")));
        if (serverUrl.isBlank() || !AuthType.NONE.name().equals(authType)
            || !"SSE".equals(transportType) || allowedTools.isEmpty()) {
            throw invalid("MCP template requires compatible transport, a reviewed tool allowlist, or user authorization");
        }

        McpServerResponse server = mcpServerService.list().stream()
            .filter(existing -> serverUrl.equals(existing.serverUrl()))
            .findFirst()
            .orElseGet(() -> mcpServerService.create(new CreateMcpServerRequest(
                draft.path("name").asText(entry.name()), serverUrl, AuthType.NONE, null, null)));

        if (server.status() == McpServerStatus.ENABLED) {
            return new MarketplaceInstallResponse(entry.id(), entry.type(), server.id(), List.of(), List.of(),
                null, "INSTALLED", true, List.of("该 MCP 已存在，未重复创建。"));
        }
        try {
            List<McpToolResponse> discovered = mcpServerService.refreshTools(server.id());
            if (discovered.isEmpty()) {
                return new MarketplaceInstallResponse(entry.id(), entry.type(), server.id(), List.of(), List.of(),
                    null, "INSTALLED", false, List.of("已添加到 MCP 设置，但服务没有返回可用工具，暂未启用。"));
            }
            long reviewedToolCount = discovered.stream().filter(tool -> allowedTools.contains(tool.toolName())).count();
            for (McpToolResponse tool : discovered) {
                if (!allowedTools.contains(tool.toolName()) && tool.enabled()) {
                    mcpServerService.setToolEnabled(server.id(), tool.id(),
                        new UpdateToolEnabledRequest(false, tool.version()));
                }
            }
            if (reviewedToolCount == 0) {
                return new MarketplaceInstallResponse(entry.id(), entry.type(), server.id(), List.of(), List.of(),
                    null, "INSTALLED", false,
                    List.of("已添加到 MCP 设置，但远程服务未返回经过广场审核的只读工具，暂未启用。"));
            }
            McpServerResponse enabled = mcpServerService.setStatus(server.id(), server.version(), McpServerStatus.ENABLED);
            return new MarketplaceInstallResponse(entry.id(), entry.type(), enabled.id(), List.of(), List.of(),
                null, "INSTALLED", true, List.of());
        } catch (Phase2ContractException exception) {
            return new MarketplaceInstallResponse(entry.id(), entry.type(), server.id(), List.of(), List.of(),
                null, "INSTALLED", false,
                List.of("已添加到 MCP 设置，但连接检测未通过（" + exception.errorCode().name()
                    + "），请稍后在 MCP 页面重试。"));
        }
    }

    private List<String> stringList(JsonNode node) {
        if (node == null || !node.isArray()) return List.of();
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) if (item.isTextual() && !item.asText().isBlank()) values.add(item.asText());
        return List.copyOf(values);
    }

    private static String catalogModelName(JsonNode draft) {
        String value = draft == null ? "" : draft.path("modelName").asText("").trim();
        if (value.isBlank() || "default".equals(value)) {
            return ModelCatalogService.SYSTEM_DEFAULT;
        }
        return value;
    }

    private void requireInstallServices() {
        if (skillPackageImportService == null || agentDefinitionService == null || agentTeamService == null || packageArchiveService == null) {
            throw new IllegalStateException("Marketplace install services are unavailable in this test seam");
        }
    }

    private void requireUser(CurrentUser user) {
        if (user == null || user.tenantId() == null || user.userId() == null) throw invalid("current user is required");
    }

    private Phase2ContractException invalid(String message) {
        return new Phase2ContractException(MvpErrorCode.VALIDATION_ERROR, message);
    }

    private record InstalledAgent(AgentResponse agent, List<String> skillIds) { }

    private List<String> missingFields(MarketplaceResourceType type, JsonNode draft) {
        List<String> missing = new ArrayList<>();
        if (draft == null) return List.of("draft");
        if (type == MarketplaceResourceType.TEAM) {
            if (draft.path("masterAgentId").isNull() || draft.path("masterAgentId").asText().isBlank()) missing.add("masterAgentId");
            if (!draft.path("memberAgentIds").isArray() || draft.path("memberAgentIds").isEmpty()) missing.add("memberAgentIds");
        } else if (type == MarketplaceResourceType.MCP) {
            if (draft.path("serverUrl").asText().isBlank()) missing.add("serverUrl");
            if (!"NONE".equals(draft.path("authType").asText())) missing.add("授权凭据（需在 MCP 设置中填写）");
        }
        return missing;
    }

    private boolean containsSensitive(JsonNode node) {
        if (node == null) return false;
        if (node.isObject()) {
            var fields = node.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                String key = field.getKey().toLowerCase(Locale.ROOT);
                if (Set.of("apikey", "token", "password", "secret", "credential", "cookie", "tenantid").contains(key)) return true;
                if (containsSensitive(field.getValue())) return true;
            }
        } else if (node.isArray()) for (JsonNode item : node) if (containsSensitive(item)) return true;
        return false;
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public static class MarketplaceNotFoundException extends RuntimeException {
        public MarketplaceNotFoundException(String id) {
            super("Marketplace resource not found: " + id);
        }
    }
}
