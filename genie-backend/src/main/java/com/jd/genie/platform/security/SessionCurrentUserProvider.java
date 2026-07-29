package com.jd.genie.platform.security;

import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.CurrentUserProvider;
import com.jd.genie.platform.contract.MvpErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class SessionCurrentUserProvider implements CurrentUserProvider {
    @Override public CurrentUser requireCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || !(authentication.getPrincipal() instanceof GenieUserPrincipal principal)) {
            throw new AuthenticationRequiredException();
        }
        return new CurrentUser(principal.getTenantId(), principal.getUserId(), principal.getUsername(), principal.getDisplayName(), principal.getRole());
    }

    public static class AuthenticationRequiredException extends RuntimeException {
        public AuthenticationRequiredException() { super(MvpErrorCode.AUTH_REQUIRED.name()); }
    }
}
