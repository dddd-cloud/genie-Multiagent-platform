package com.jd.genie.platform.user.controller;

import com.jd.genie.platform.contract.ApiResponse;
import com.jd.genie.platform.contract.CurrentUserProvider;
import com.jd.genie.platform.contract.PageResponse;
import com.jd.genie.platform.user.dto.AdminUserResponse;
import com.jd.genie.platform.user.dto.CreateAdminUserRequest;
import com.jd.genie.platform.user.dto.ResetUserPasswordRequest;
import com.jd.genie.platform.user.dto.UpdateUserStatusRequest;
import com.jd.genie.platform.user.service.AdminUserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {
    private final CurrentUserProvider currentUserProvider;
    private final AdminUserService adminUserService;
    public AdminUserController(CurrentUserProvider currentUserProvider, AdminUserService adminUserService) {
        this.currentUserProvider = currentUserProvider; this.adminUserService = adminUserService;
    }
    @GetMapping
    public ApiResponse<PageResponse<AdminUserResponse>> list(@RequestParam(defaultValue = "1") int page,
                                                               @RequestParam(defaultValue = "20") int pageSize) {
        return new ApiResponse<>("OK", "success", adminUserService.list(currentUserProvider.requireCurrentUser().tenantId(), page, pageSize));
    }
    @PostMapping
    public ApiResponse<AdminUserResponse> create(@RequestBody CreateAdminUserRequest request) {
        return new ApiResponse<>("OK", "success", adminUserService.create(currentUserProvider.requireCurrentUser().tenantId(), request));
    }
    @PatchMapping("/{userId}/status")
    public ApiResponse<AdminUserResponse> status(@PathVariable String userId, @RequestBody UpdateUserStatusRequest request) {
        return new ApiResponse<>("OK", "success", adminUserService.updateStatus(currentUserProvider.requireCurrentUser().tenantId(), userId, request == null ? null : request.status()));
    }
    @PostMapping("/{userId}/reset-password")
    public ApiResponse<Void> resetPassword(@PathVariable String userId, @RequestBody ResetUserPasswordRequest request) {
        adminUserService.resetPassword(currentUserProvider.requireCurrentUser().tenantId(), userId, request == null ? null : request.newPassword());
        return new ApiResponse<>("OK", "success", null);
    }
}
