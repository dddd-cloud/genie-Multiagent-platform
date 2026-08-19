package com.jd.genie.platform.marketplace;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record MarketplaceCatalogEntry(
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
    JsonNode draft,
    JsonNode delivery
) {
    public MarketplaceCatalogEntry {
        tags = tags == null ? List.of() : List.copyOf(tags);
        capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
        setup = setup == null ? List.of() : List.copyOf(setup);
    }
}
