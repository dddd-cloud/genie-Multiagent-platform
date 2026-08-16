package com.jd.genie.platform.phase2.configuration.memory.dto;

public record MemoryFileResponse(
    String status,
    String markdown,
    String reason
) {
}
