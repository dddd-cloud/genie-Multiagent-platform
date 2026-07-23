package com.jd.genie.platform.security;

import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** Request-only authentication for the fixed internal Agent endpoint. */
public final class InternalAgentAuthenticationToken extends AbstractAuthenticationToken {
    public static final String AUTHORITY = "INTERNAL_AGENT";

    private InternalAgentAuthenticationToken() {
        super(List.of(new SimpleGrantedAuthority(AUTHORITY)));
        setAuthenticated(true);
    }

    public static InternalAgentAuthenticationToken authenticated() {
        return new InternalAgentAuthenticationToken();
    }

    @Override public Object getCredentials() { return null; }
    @Override public Object getPrincipal() { return "internal-agent"; }
    @Override public String toString() { return "InternalAgentAuthenticationToken[authority=INTERNAL_AGENT]"; }
}
