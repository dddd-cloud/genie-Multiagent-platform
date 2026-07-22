package com.jd.genie.platform.user.controller;

import com.jd.genie.platform.contract.ApiResponse;
import com.jd.genie.platform.contract.CurrentUserProvider;
import com.jd.genie.platform.user.dto.AuthUserResponse;
import com.jd.genie.platform.user.dto.CsrfTokenResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthUtilityController {
    private final CurrentUserProvider currentUserProvider;
    public AuthUtilityController(CurrentUserProvider currentUserProvider) { this.currentUserProvider = currentUserProvider; }

    @GetMapping("/api/v1/auth/csrf")
    public ApiResponse<CsrfTokenResponse> csrf(CsrfToken token) {
        return new ApiResponse<>("OK", "success", new CsrfTokenResponse(token.getHeaderName(), token.getParameterName(), token.getToken()));
    }

    @GetMapping("/api/v1/users/me")
    public ApiResponse<AuthUserResponse> me() {
        var user = currentUserProvider.requireCurrentUser();
        return new ApiResponse<>("OK", "success", new AuthUserResponse(user.userId(), user.username(), user.displayName(), user.role()));
    }
}
