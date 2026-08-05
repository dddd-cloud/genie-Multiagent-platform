package com.jd.genie.platform.phase2.configuration.memory.dto;

public record MemoryPatchItem(
    String operation,
    String section,
    String key,
    String value
) {
}
