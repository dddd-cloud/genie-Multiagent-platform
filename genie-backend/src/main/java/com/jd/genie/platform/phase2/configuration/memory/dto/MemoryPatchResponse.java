package com.jd.genie.platform.phase2.configuration.memory.dto;

import java.util.List;

public record MemoryPatchResponse(
    int schemaVersion,
    List<MemoryPatchItem> patches
) {
    public MemoryPatchResponse {
        patches = patches == null ? List.of() : List.copyOf(patches);
    }
}
