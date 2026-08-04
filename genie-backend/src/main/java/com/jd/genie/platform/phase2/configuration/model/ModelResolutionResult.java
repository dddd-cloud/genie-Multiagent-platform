package com.jd.genie.platform.phase2.configuration.model;

public record ModelResolutionResult(
    String storedModelName,
    String resolvedModelName,
    boolean available
) {
}
