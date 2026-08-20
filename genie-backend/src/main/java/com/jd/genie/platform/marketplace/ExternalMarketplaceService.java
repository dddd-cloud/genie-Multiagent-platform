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
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Read-only adapters for public registries. Packages still enter through the normal validated import path. */
@Service
public class ExternalMarketplaceService {
    private static final int SEARCH_LIMIT = 30;
    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
    private final ObjectMapper mapper;
    private final SkillPackageImportService skillImport;
    private final HttpClient http;
    private final String skillHubApi;
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
        this.mcpRegistryApi = trimSlash(mcpRegistryApi);
        this.http = http;
    }

    public List<ExternalMarketplaceResource> search(ExternalMarketplaceSource source, String query, String sort) {
        if (source == null) throw invalid("external source required");
        return source == ExternalMarketplaceSource.SKILLHUB ? searchSkillHub(query, sort) : searchMcpRegistry(query, sort);
    }

    public SkillResponse installSkillHub(CurrentUser user, ExternalMarketplaceInstallRequest request) {
        if (request == null || blank(request.slug()) || blank(request.version())) throw invalid("skill slug and version required");
        String url = skillHubApi + "/download?slug=" + encode(request.slug()) + "&version=" + encode(request.version());
        byte[] archive = getBytes(url, SkillPackageLimits.MAX_IMPORT_ZIP_BYTES);
        return skillImport.importPackage(user, archive, null);
    }

    private List<ExternalMarketplaceResource> searchSkillHub(String query, String sort) {
        String url = skillHubApi + "/search?q=" + encode(query == null ? "" : query) + "&limit=" + SEARCH_LIMIT;
        JsonNode results = getJson(url).path("results");
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
        // The upstream search order carries its relevance score.  Applying the
        // marketplace popularity sort to a non-empty query hides exact matches
        // behind unrelated but popular Skills (for example "JSON Toolkit").
        return blank(query) ? sort(out, sort) : List.copyOf(out);
    }

    private List<ExternalMarketplaceResource> searchMcpRegistry(String query, String sort) {
        String url = mcpRegistryApi + "/servers?search=" + encode(query == null ? "" : query) + "&limit=" + SEARCH_LIMIT;
        JsonNode servers = getJson(url).path("servers");
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
                ? "需要 Streamable HTTP 运行时适配" : (remoteUrl.isBlank() ? "仅提供本地安装包" : "可在 MCP 设置中检测并启用");
            out.add(new ExternalMarketplaceResource(ExternalMarketplaceSource.MCP_REGISTRY, MarketplaceResourceType.MCP,
                name, first(text(server, "version"), "latest"), name, description, "官方 MCP Registry",
                List.of(transport), 0, 0, "https://registry.modelcontextprotocol.io", text(server.path("repository"), "url"),
                remoteUrl, transport, needsCredential, compatibility));
        }
        return sort(out, sort);
    }

    private List<ExternalMarketplaceResource> sort(List<ExternalMarketplaceResource> values, String sort) {
        Comparator<ExternalMarketplaceResource> comparator = "downloads".equalsIgnoreCase(sort)
            ? Comparator.comparingLong(ExternalMarketplaceResource::downloads).reversed()
            : Comparator.comparingLong(ExternalMarketplaceResource::stars).reversed();
        return values.stream().sorted(comparator.thenComparing(ExternalMarketplaceResource::name, String.CASE_INSENSITIVE_ORDER)).toList();
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
