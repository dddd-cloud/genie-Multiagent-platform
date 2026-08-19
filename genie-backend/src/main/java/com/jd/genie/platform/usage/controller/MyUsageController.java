package com.jd.genie.platform.usage.controller;

import com.jd.genie.platform.contract.ApiResponse;
import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.CurrentUserProvider;
import com.jd.genie.platform.usage.dto.UsageSummaryResponse;
import com.jd.genie.platform.usage.service.UsageQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Self-service usage. The user id comes from the session only, so a caller cannot read someone
 * else's usage by passing a foreign id.
 */
@RestController
@RequestMapping("/api/v1/me/usage")
public class MyUsageController {
    private final CurrentUserProvider currentUserProvider;
    private final UsageQueryService usageQueryService;

    public MyUsageController(CurrentUserProvider currentUserProvider, UsageQueryService usageQueryService) {
        this.currentUserProvider = currentUserProvider;
        this.usageQueryService = usageQueryService;
    }

    @GetMapping("/summary")
    public ApiResponse<UsageSummaryResponse> summary(@RequestParam(required = false) String from,
                                                     @RequestParam(required = false) String to) {
        CurrentUser currentUser = currentUserProvider.requireCurrentUser();
        return new ApiResponse<>("OK", "success",
            usageQueryService.userSummary(currentUser.tenantId(), currentUser.userId(), from, to));
    }
}
