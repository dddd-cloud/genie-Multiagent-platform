package com.jd.genie.platform.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.user.dto.AuthUserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.context.SecurityContextRepository;

public class JsonAuthenticationSuccessHandler implements AuthenticationSuccessHandler {
    private final ObjectMapper objectMapper;
    private final SecurityContextRepository repository;
    public JsonAuthenticationSuccessHandler(ObjectMapper objectMapper, SecurityContextRepository repository) { this.objectMapper = objectMapper; this.repository = repository; }
    @Override public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        repository.saveContext(context, request, response);
        GenieUserPrincipal principal = (GenieUserPrincipal) authentication.getPrincipal();
        JsonApiWriter.write(objectMapper, response, 200, "OK", "success", new AuthUserResponse(principal.getUserId(), principal.getUsername(), principal.getDisplayName(), principal.getRole()));
    }
}
