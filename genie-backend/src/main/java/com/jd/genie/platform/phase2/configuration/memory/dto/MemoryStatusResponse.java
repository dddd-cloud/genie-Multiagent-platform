package com.jd.genie.platform.phase2.configuration.memory.dto;

public record MemoryStatusResponse(
    boolean available,
    String rootPath,
    String userId
) {
}
