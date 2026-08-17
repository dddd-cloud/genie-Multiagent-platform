package com.jd.genie.platform.phase2contract.port;

import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.phase2contract.dto.TeamRuntimeSelection;

public interface TeamRuntimeCatalogPort {

    /**
     * Resolves an owned team into its master persona and its online executor candidates.
     * Members that went offline after the team was created are skipped rather than failing the run.
     */
    TeamRuntimeSelection resolve(CurrentUser user, String teamId);
}
