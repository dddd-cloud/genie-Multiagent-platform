package com.jd.genie.platform.settings.controller;

import com.jd.genie.platform.contract.ApiResponse;
import com.jd.genie.platform.contract.CurrentUserProvider;
import com.jd.genie.platform.settings.dto.UpdateUserSettingsRequest;
import com.jd.genie.platform.settings.dto.UserSettingsResponse;
import com.jd.genie.platform.settings.service.UserSettingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/settings")
public class UserSettingController {
    private final CurrentUserProvider currentUserProvider;
    private final UserSettingService userSettingService;

    public UserSettingController(CurrentUserProvider currentUserProvider, UserSettingService userSettingService) {
        this.currentUserProvider = currentUserProvider;
        this.userSettingService = userSettingService;
    }

    @GetMapping
    public ApiResponse<UserSettingsResponse> get() {
        return new ApiResponse<>("OK", "success", userSettingService.get(currentUserProvider.requireCurrentUser()));
    }

    @PutMapping
    public ApiResponse<UserSettingsResponse> update(@RequestBody UpdateUserSettingsRequest request) {
        return new ApiResponse<>("OK", "success",
            userSettingService.update(currentUserProvider.requireCurrentUser(), request));
    }
}
