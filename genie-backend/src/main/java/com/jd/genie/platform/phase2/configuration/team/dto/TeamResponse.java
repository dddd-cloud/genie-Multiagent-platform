package com.jd.genie.platform.phase2.configuration.team.dto;

import java.time.Instant;
import java.util.List;

public record TeamResponse(
    String id,
    String name,
    String description,
    String masterAgentId,
    String masterAgentName,
    List<String> memberAgentIds,
    Long version,
    Instant createdAt,
    Instant updatedAt
) {
}
