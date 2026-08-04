package com.jd.genie.platform.phase2.configuration.model;

public record ModelCatalogItem(
    String name,
    String displayName,
    boolean isDefault,
    boolean available
) {
}
