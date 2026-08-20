package com.jd.genie.platform.marketplace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2.configuration.skill.dto.SkillResponse;
import com.jd.genie.platform.phase2.configuration.skill.service.SkillPackageImportService;
import com.jd.genie.platform.phase2.skillruntime.packageinfo.SkillPackageLimits;
import com.jd.genie.platform.phase2contract.error.Phase2ContractException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Read-only adapters for public registries. Packages still enter through the normal validated import path. */
@Service
public class ExternalMarketplaceService {
    static final int DEFAULT_PAGE_SIZE = 12;
    static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
    private final ObjectMapper mapper;
    private final SkillPackageImportService skillImport;
    private final HttpClient http;
    private final String skillHubApi;
    private final String skillHubListApi;
    private final String mcpRegistryApi;

    @Autowired
    public ExternalMarketplaceService(ObjectMapper mapper, SkillPackageImportService skillImport,
            @Value("${genie.marketplace.skillhub-api:https://api.skillhub.cn/api/v1}") String skillHubApi,
            @Value("${genie.marketplace.mcp-registry-api:https://registry.modelcontextprotocol.io/v0.1}") String mcpRegistryApi) {
        this(mapper, skillImport, skillHubApi, mcpRegistryApi,
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NORMAL).build());
    }

    ExternalMarketplaceService(ObjectMapper mapper, SkillPackageImportService skillImport, String skillHubApi,
            String mcpRegistryApi, HttpClient http) {
        this.mapper = mapper;
        this.skillImport = skillImport;
        this.skillHubApi = trimSlash(skillHubApi);
        this.skillHubListApi = skillHubCatalogUrl(this.skillHubApi);
        this.mcpRegistryApi = trimSlash(mcpRegistryApi);
        this.http = http;
    }

    public ExternalMarketplacePage search(ExternalMarketplaceSource source, String query, String sort,
            Integer limit, String cursor) {
        if (source == null) throw invalid("external source required");
        int pageSize = clampLimit(limit);
        return source == ExternalMarketplaceSource.SKILLHUB
            ? searchSkillHub(query, sort, pageSize, cursor)
            : searchMcpRegistry(query, pageSize, cursor);
    }

    public SkillResponse installSkillHub(CurrentUser user, ExternalMarketplaceInstallRequest request) {
        if (request == null || blank(request.slug()) || blank(request.version())) throw invalid("skill slug and version required");
        String url = skillHubApi + "/download?slug=" + encode(request.slug()) + "&version=" + encode(request.version());
        byte[] archive = getBytes(url, SkillPackageLimits.MAX_IMPORT_ZIP_BYTES);
        return skillImport.importPackage(user, archive, null);
    }

    private ExternalMarketplacePage searchSkillHub(String query, String sort, int pageSize, String cursor) {
        int page = parsePage(cursor);
        String url = skillHubListApi + "?page=" + page + "&pageSize=" + pageSize;
        if (!blank(query)) url += "&keyword=" + encode(query);
        if (!blank(sort)) url += "&sort=" + encode(sort);
        JsonNode root = getJson(url);
        JsonNode data = root.path("data");
        JsonNode results = data.path("skills");
        if (!results.isArray()) results = root.path("results");
        if (!results.isArray()) results = root.path("items");
        if (!results.isArray()) throw unavailable("SkillHub response invalid");
        List<ExternalMarketplaceResource> out = new ArrayList<>();
        for (JsonNode item : results) {
            String slug = text(item, "slug");
            if (blank(slug)) continue;
            String name = first(text(item, "displayName"), text(item, "name"), slug);
            String description = first(text(item, "description_zh"), text(item, "description"), text(item, "summary"));
            List<String> tags = strings(item.path("tags"));
            String category = first(text(item, "category"), "未分类");
            String version = first(text(item, "version"), "latest");
            String sourceUrl = first(text(item, "homepage"), "https://www.skillhub.cn/skills/" + slug);
            boolean needsKey = item.path("labels").path("requires_api_key").asBoolean(false)
                || "true".equalsIgnoreCase(item.path("labels").path("requires_api_key").asText());
            out.add(new ExternalMarketplaceResource(ExternalMarketplaceSource.SKILLHUB, MarketplaceResourceType.SKILL,
                slug, version, name, description, category, tags, item.path("stars").asLong(), item.path("downloads").asLong(),
                sourceUrl, "", "", "SKILL.md", needsKey, "导入后按 JoyAgent Skill 包校验"));
        }
        int total = data.path("total").asInt(-1);
        if (total < 0) total = root.path("total").asInt(-1);
        int offset = (page - 1) * pageSize;
        boolean hasMore = total >= 0 ? offset + out.size() < total : out.size() >= pageSize;
        return new ExternalMarketplacePage(out, hasMore, hasMore ? String.valueOf(page + 1) : null);
    }

    private ExternalMarketplacePage searchMcpRegistry(String query, int pageSize, String cursor) {
        String url = mcpRegistryApi + "/servers?search=" + encode(query == null ? "" : query) + "&limit=" + pageSize;
        if (!blank(cursor)) url += "&cursor=" + encode(cursor);
        JsonNode root = getJson(url);
        JsonNode servers = root.path("servers");
        if (!servers.isArray()) throw unavailable("MCP Registry response invalid");
        List<ExternalMarketplaceResource> out = new ArrayList<>();
        for (JsonNode envelope : servers) {
            JsonNode server = envelope.path("server");
            String name = text(server, "name");
            if (blank(name)) continue;
            JsonNode remote = server.path("remotes").isArray() && !server.path("remotes").isEmpty() ? server.path("remotes").get(0) : null;
            String transport = remote == null ? "未提供远程连接" : text(remote, "type");
            String remoteUrl = remote == null ? "" : text(remote, "url");
            String description = first(text(server, "description"), "");
            String authHint = (name + " " + description).toLowerCase(java.util.Locale.ROOT);
            boolean needsCredential = remote != null && remote.path("headers").isArray() && remote.path("headers").size() > 0
                || authHint.contains("api key") || authHint.contains("oauth") || authHint.contains("bearer")
      || authHint.contains("requires authentication") || authHint.contains("signup");
      needsCredential = needsCredential || authHint.contains("pay per run") || authHint.contains("x402") || authHint.contains("paid") || authHint.contains("payment");
            String compatibility = "streamable-http".equalsIgnoreCase(transport)
                ? "需要 Streamable HTTP 运行时适配" : (remoteUrl.isBlank() ? "仅提供本地安装包" : "可在连接器中检测并启用");
            out.add(new ExternalMarketplaceResource(ExternalMarketplaceSource.MCP_REGISTRY, MarketplaceResourceType.MCP,
                name, first(text(server, "version"), "latest"), name, description, "官方 MCP Registry",
                List.of(transport), 0, 0, "https://registry.modelcontextprotocol.io", text(server.path("repository"), "url"),
                remoteUrl, transport, needsCredential, compatibility));
        }
        String nextCursor = first(text(root.path("metadata"), "nextCursor"), text(root.path("metadata"), "next_cursor"));
        boolean hasMore = !blank(nextCursor);
        return new ExternalMarketplacePage(out, hasMore, hasMore ? nextCursor : null);
    }

    private static int clampLimit(Integer limit) {
        if (limit == null || limit < 1) return DEFAULT_PAGE_SIZE;
        return Math.min(limit, MAX_PAGE_SIZE);
    }

    private static int parsePage(String cursor) {
        if (blank(cursor)) return 1;
        try {
            return Math.max(1, Integer.parseInt(cursor.trim()));
        } catch (NumberFormatException ex) {
            return 1;
        }
    }

    /** Public catalog lives at /api/skills; install/download stay on the /api/v1 compatibility API. */
    private static String skillHubCatalogUrl(String skillHubApi) {
        if (skillHubApi.endsWith("/api/v1")) {
            return skillHubApi.substring(0, skillHubApi.length() - "/v1".length()) + "/skills";
        }
        if (skillHubApi.endsWith("/v1")) {
            return skillHubApi.substring(0, skillHubApi.length() - 3) + "/skills";
        }
        return skillHubApi + "/skills";
    }

    private JsonNode getJson(String url) {
        try { return mapper.readTree(getBytes(url, MAX_RESPONSE_BYTES)); }
        catch (Exception ex) { throw unavailable("external marketplace unavailable"); }
    }

    private byte[] getBytes(String url, long maxBytes) {
        try {
            HttpResponse<byte[]> response = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() / 100 != 2 || response.body().length > maxBytes) throw unavailable("external marketplace download unavailable");
            return response.body();
        } catch (Phase2ContractException ex) { throw ex; }
        catch (Exception ex) { throw unavailable("external marketplace unavailable"); }
    }

    private static String text(JsonNode node, String field) { return node.path(field).asText("").trim(); }
    private static List<String> strings(JsonNode node) { List<String> out = new ArrayList<>(); if (node.isArray()) for (JsonNode v : node) if (v.isTextual() && !v.asText().isBlank()) out.add(v.asText()); return List.copyOf(out); }
    private static String first(String... values) { for (String value : values) if (!blank(value)) return value; return ""; }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static String trimSlash(String value) { return value == null ? "" : value.replaceAll("/+$", ""); }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static Phase2ContractException invalid(String message) { return new Phase2ContractException(MvpErrorCode.VALIDATION_ERROR, message); }
    private static Phase2ContractException unavailable(String message) { return new Phase2ContractException(MvpErrorCode.MCP_UNAVAILABLE, message); }
}
