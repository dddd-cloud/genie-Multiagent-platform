package com.jd.genie.platform.phase2.configuration.team.dto;

import java.util.List;

public record TeamCreateRequest(
    String name,
    String description,
    String masterAgentId,
    List<String> memberAgentIds
) {
}
