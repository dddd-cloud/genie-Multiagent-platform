package com.jd.genie.platform.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.platform.contract.MvpErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

public class JsonAuthenticationFailureHandler implements AuthenticationFailureHandler {
    private final ObjectMapper objectMapper;
    public JsonAuthenticationFailureHandler(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }
    @Override public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException {
        JsonApiWriter.write(objectMapper, response, 401, MvpErrorCode.AUTH_INVALID_CREDENTIALS.name(), "Invalid credentials", null);
    }
}
