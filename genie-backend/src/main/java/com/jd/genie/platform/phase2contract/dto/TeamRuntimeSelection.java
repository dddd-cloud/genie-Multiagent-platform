package com.jd.genie.platform.phase2contract.dto;

import java.util.List;

/**
 * Resolved runtime view of a team: the master persona overlay plus the executor candidates.
 * The master Agent is never part of {@code memberCandidates} so it cannot be assigned a step.
 */
public record TeamRuntimeSelection(
    String teamId,
    String teamName,
    MasterPersona masterPersona,
    List<AgentCapabilitySummary> memberCandidates
) {
}
