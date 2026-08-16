package com.jd.genie.platform.phase2.configuration.memory.dto;

import java.util.List;

public record MemorySummaryIndexResponse(
    List<MemorySummaryIndexItemResponse> items
) {
}
