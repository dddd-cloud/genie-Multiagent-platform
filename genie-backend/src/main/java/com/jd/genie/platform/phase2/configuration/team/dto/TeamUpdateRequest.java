package com.jd.genie.platform.phase2.configuration.team.dto;

import java.util.List;

public record TeamUpdateRequest(
    String name,
    String description,
    String masterAgentId,
    List<String> memberAgentIds,
    Long version
) {
}
