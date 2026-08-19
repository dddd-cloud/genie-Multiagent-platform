package com.jd.genie.platform.usage.controller;

import com.jd.genie.platform.contract.ApiResponse;
import com.jd.genie.platform.contract.CurrentUserProvider;
import com.jd.genie.platform.contract.PageResponse;
import com.jd.genie.platform.usage.dto.UsageSummaryResponse;
import com.jd.genie.platform.usage.dto.UsageUserRow;
import com.jd.genie.platform.usage.service.UsageQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Tenant-wide usage. Authorization is enforced by the /api/v1/admin/** ROLE_ADMIN rule. */
@RestController
@RequestMapping("/api/v1/admin/usage")
public class AdminUsageController {
    private final CurrentUserProvider currentUserProvider;
    private final UsageQueryService usageQueryService;

    public AdminUsageController(CurrentUserProvider currentUserProvider, UsageQueryService usageQueryService) {
        this.currentUserProvider = currentUserProvider;
        this.usageQueryService = usageQueryService;
    }

    @GetMapping("/summary")
    public ApiResponse<UsageSummaryResponse> summary(@RequestParam(required = false) String from,
                                                    @RequestParam(required = false) String to) {
        return new ApiResponse<>("OK", "success",
            usageQueryService.tenantSummary(currentUserProvider.requireCurrentUser().tenantId(), from, to));
    }

    @GetMapping("/users")
    public ApiResponse<PageResponse<UsageUserRow>> users(@RequestParam(required = false) String from,
                                                         @RequestParam(required = false) String to,
                                                         @RequestParam(defaultValue = "1") int page,
                                                         @RequestParam(defaultValue = "20") int pageSize) {
        return new ApiResponse<>("OK", "success", usageQueryService.userBreakdown(
            currentUserProvider.requireCurrentUser().tenantId(), from, to, page, pageSize));
    }
}
