package com.jd.genie.platform.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.platform.contract.UserRole;
import com.jd.genie.platform.user.entity.UserEntity;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InternalAgentAuthFilterTest {
    private static final String TOKEN = "phase5-test-token";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach void clearContext() { SecurityContextHolder.clearContext(); }

    @Test void rejectsMissingEmptyWrongCasePrefixAndSuffixTokensWithoutRunningTheChain() throws Exception {
        for (String token : new String[] {null, "", "wrong", "PHASE5-TEST-TOKEN", "phase5-test", "xphase5-test-token"}) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            AtomicInteger calls = new AtomicInteger();
            MockHttpServletRequest request = request(token);
            filter().doFilter(request, response, (req, res) -> calls.incrementAndGet());
            JsonNode body = objectMapper.readTree(response.getContentAsString());
            assertEquals(401, response.getStatus());
            assertEquals("INTERNAL_TOKEN_INVALID", body.get("code").asText());
            assertTrueDataIsNull(body);
            assertEquals(0, calls.get());
            assertFalse(response.getContentAsString().contains(TOKEN));
        }
    }

    @Test void acceptsOnlyTheExactTokenAndClearsTheRequestContextAfterward() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicInteger calls = new AtomicInteger();
        filter().doFilter(request(TOKEN), response, (req, res) -> {
            calls.incrementAndGet();
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            assertInstanceOf(InternalAgentAuthenticationToken.class, authentication);
            assertEquals(InternalAgentAuthenticationToken.AUTHORITY, authentication.getAuthorities().iterator().next().getAuthority());
            assertFalse(authentication.getPrincipal() instanceof GenieUserPrincipal);
            assertNull(authentication.getCredentials());
        });
        assertEquals(1, calls.get());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test void currentUserProviderRejectsInternalAuthenticationButAcceptsAUserPrincipal() {
        SessionCurrentUserProvider provider = new SessionCurrentUserProvider();
        SecurityContextHolder.getContext().setAuthentication(InternalAgentAuthenticationToken.authenticated());
        assertThrows(SessionCurrentUserProvider.AuthenticationRequiredException.class, provider::requireCurrentUser);

        UserEntity user = new UserEntity();
        user.setId("user-1"); user.setTenantId("tenant-1"); user.setUsername("user");
        user.setDisplayName("User"); user.setRole(UserRole.USER); user.setPasswordHash("hash");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(GenieUserPrincipal.from(user), null,
            GenieUserPrincipal.from(user).getAuthorities()));
        assertEquals("user-1", provider.requireCurrentUser().userId());
    }

    private InternalAgentAuthFilter filter() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("test");
        environment.setProperty("GENIE_INTERNAL_AGENT_TOKEN", TOKEN);
        return new InternalAgentAuthFilter(new SecurityProperties(environment), objectMapper);
    }

    private MockHttpServletRequest request(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/AutoAgent");
        if (token != null) request.addHeader(InternalAgentAuthFilter.HEADER, token);
        return request;
    }

    private void assertTrueDataIsNull(JsonNode body) {
        assertEquals(true, body.get("data").isNull());
    }
}
