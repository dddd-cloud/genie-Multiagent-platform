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
    List<String> setup
) {
    static MarketplaceResourceView from(MarketplaceCatalogEntry entry) {
        return new MarketplaceResourceView(
            entry.id(), entry.type(), entry.slug(), entry.name(), entry.tagline(),
            entry.description(), entry.category(), entry.tags(), entry.sourceType(),
            entry.sourceUrl(), entry.license(), entry.trustTier(), entry.capabilities(), entry.setup()
        );
    }
}
