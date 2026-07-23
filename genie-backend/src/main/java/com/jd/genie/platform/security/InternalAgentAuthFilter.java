package com.jd.genie.platform.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.platform.contract.MvpErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Validates the sole internal-token ingress without creating or saving a browser Session. */
public final class InternalAgentAuthFilter extends OncePerRequestFilter {
    static final String HEADER = "X-Genie-Internal-Token";
    private final SecurityProperties securityProperties;
    private final ObjectMapper objectMapper;

    public InternalAgentAuthFilter(SecurityProperties securityProperties, ObjectMapper objectMapper) {
        this.securityProperties = securityProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !(request.getContextPath() + "/AutoAgent").equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        String supplied = request.getHeader(HEADER);
        String expected = securityProperties.internalAgentToken();
        if (supplied == null || supplied.isEmpty() || expected == null || expected.isEmpty()
            || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8))) {
            JsonApiWriter.write(objectMapper, response, HttpServletResponse.SC_UNAUTHORIZED,
                MvpErrorCode.INTERNAL_TOKEN_INVALID.name(), "Internal token invalid", null);
            return;
        }

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(InternalAgentAuthenticationToken.authenticated());
        SecurityContextHolder.setContext(context);
        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
