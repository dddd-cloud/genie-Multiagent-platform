package com.jd.genie.platform.marketplace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.platform.contract.CurrentUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Service
public class MarketplaceResourceService {
    private final ObjectMapper objectMapper;
    private final List<MarketplaceCatalogEntry> catalog;

    @Autowired
    public MarketplaceResourceService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.catalog = loadCatalog();
    }

    /** Test seam: parse a catalog document without touching the classpath file. */
    MarketplaceResourceService(ObjectMapper objectMapper, String catalogJson) {
        this.objectMapper = objectMapper;
        this.catalog = parseCatalogDocument(catalogJson);
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
        return null;
    }

    private List<String> missingFields(MarketplaceResourceType type, JsonNode draft) {
        List<String> missing = new ArrayList<>();
        if (draft == null) return List.of("draft");
        if (type == MarketplaceResourceType.TEAM) {
            if (draft.path("masterAgentId").isNull() || draft.path("masterAgentId").asText().isBlank()) missing.add("masterAgentId");
            if (!draft.path("memberAgentIds").isArray() || draft.path("memberAgentIds").isEmpty()) missing.add("memberAgentIds");
        } else if (type == MarketplaceResourceType.MCP) {
            if (draft.path("serverUrl").asText().isBlank()) missing.add("serverUrl");
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
