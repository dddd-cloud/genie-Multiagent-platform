package com.jd.genie.platform.phase2contract.dto;

import java.util.List;

public record BrowserSkillExecutionManifest(
    int schemaVersion,
    String executionId,
    String entrypointName,
    String scriptRelativePath,
    List<String> packages,
    String inputJson
) {
    public BrowserSkillExecutionManifest {
        packages = packages == null ? List.of() : List.copyOf(packages);
    }
}
