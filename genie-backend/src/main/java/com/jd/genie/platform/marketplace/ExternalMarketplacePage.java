package com.jd.genie.platform.marketplace;

import java.util.List;

public record ExternalMarketplacePage(
    List<ExternalMarketplaceResource> items,
    boolean hasMore,
    String nextCursor
) {
    public ExternalMarketplacePage {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
