package com.jd.genie.platform.marketplace;

import java.util.List;

public record MarketplaceResourceView(
    String id,
    MarketplaceResourceType type,
    String slug,
    String name,
    String tagline,
    String description,
    String category,
    List<String> tags,
    String sourceType,
    String sourceUrl,
    String license,
    String trustTier,
    List<String> capabilities,
    List<String> setup,
    String installMode
) {
    static MarketplaceResourceView from(MarketplaceCatalogEntry entry) {
        String installMode = "INSTALL";
        if (entry.type() == MarketplaceResourceType.MCP) {
            String serverUrl = entry.draft() == null ? "" : entry.draft().path("serverUrl").asText("").trim();
            String authType = entry.draft() == null ? "" : entry.draft().path("authType").asText("").trim();
            String transportType = entry.draft() == null ? "" : entry.draft().path("transportType").asText("").trim();
            boolean hasAllowlist = entry.draft() != null && entry.draft().path("allowedTools").isArray()
                && !entry.draft().path("allowedTools").isEmpty();
            installMode = !serverUrl.isBlank() && "NONE".equals(authType) && "SSE".equals(transportType)
                && hasAllowlist ? "INSTALL" : "CONFIGURE";
        }
        return new MarketplaceResourceView(
            entry.id(), entry.type(), entry.slug(), entry.name(), entry.tagline(),
            entry.description(), entry.category(), entry.tags(), entry.sourceType(),
            entry.sourceUrl(), entry.license(), entry.trustTier(), entry.capabilities(), entry.setup(), installMode
        );
    }
}
