package com.jd.genie.platform.phase2contract.dto;

import java.util.List;

/**
 * Compact team card for AUTO dispatch. Only teams that can actually run are listed.
 */
public record TeamCapabilitySummary(
        String teamId,
        String name,
        String description,
        String masterAgentName,
        List<String> memberNames
) {
    public TeamCapabilitySummary {
        memberNames = memberNames == null ? List.of() : List.copyOf(memberNames);
        description = description == null ? "" : description;
        masterAgentName = masterAgentName == null ? "" : masterAgentName;
    }
}
