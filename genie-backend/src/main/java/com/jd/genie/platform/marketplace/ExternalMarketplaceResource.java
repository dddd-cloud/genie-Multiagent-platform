package com.jd.genie.platform.marketplace;

import java.util.List;

public record ExternalMarketplaceResource(
    ExternalMarketplaceSource source,
    MarketplaceResourceType type,
    String slug,
    String version,
    String name,
    String description,
    String category,
    List<String> tags,
    long stars,
    long downloads,
    String sourceUrl,
    String repositoryUrl,
    String remoteUrl,
    String transport,
    boolean requiresCredential,
    String compatibility
) {
    public ExternalMarketplaceResource {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
