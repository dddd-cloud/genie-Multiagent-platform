package com.jd.genie.platform.marketplace;

import java.util.List;

/** Result of materialising a curated marketplace entry as resources owned by the current user. */
public record MarketplaceInstallResponse(
    String marketplaceResourceId,
    MarketplaceResourceType resourceType,
    String primaryResourceId,
    List<String> createdAgentIds,
    List<String> createdSkillIds,
    String createdTeamId,
    String status,
    boolean enabled,
    List<String> warnings
) {
    public MarketplaceInstallResponse {
        createdAgentIds = createdAgentIds == null ? List.of() : List.copyOf(createdAgentIds);
        createdSkillIds = createdSkillIds == null ? List.of() : List.copyOf(createdSkillIds);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
