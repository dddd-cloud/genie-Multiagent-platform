package com.jd.genie.platform.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

public class JsonUsernamePasswordAuthenticationFilter extends AbstractAuthenticationProcessingFilter {
    private final ObjectMapper objectMapper;
    public JsonUsernamePasswordAuthenticationFilter(AuthenticationManager authenticationManager, ObjectMapper objectMapper) {
        super(new AntPathRequestMatcher("/api/v1/auth/login", "POST"), authenticationManager);
        this.objectMapper = objectMapper;
    }
    @Override public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) throws AuthenticationException {
        try {
            LoginPayload payload = objectMapper.readValue(request.getInputStream(), LoginPayload.class);
            String username = payload.username() == null ? "" : payload.username();
            String password = payload.password() == null ? "" : payload.password();
            return getAuthenticationManager().authenticate(UsernamePasswordAuthenticationToken.unauthenticated(username, password));
        } catch (IOException exception) {
            return getAuthenticationManager().authenticate(UsernamePasswordAuthenticationToken.unauthenticated("", ""));
        }
    }
    private record LoginPayload(String username, String password) { }
}
