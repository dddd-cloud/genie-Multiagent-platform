package com.jd.genie.platform.marketplace;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record MarketplaceDraftResponse(
    String resourceId,
    MarketplaceResourceType type,
    String name,
    String ownerUserId,
    JsonNode draft,
    List<String> warnings,
    String status,
    List<String> missingFields
) {
}
