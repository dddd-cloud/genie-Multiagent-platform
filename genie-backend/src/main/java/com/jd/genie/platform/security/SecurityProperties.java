package com.jd.genie.platform.security;

import java.util.Arrays;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Private A-module mapping for the frozen GENIE_INTERNAL_AGENT_TOKEN environment variable. */
@Component
public final class SecurityProperties {
    private final String internalAgentToken;

    public SecurityProperties(Environment environment) {
        this.internalAgentToken = environment.getProperty("GENIE_INTERNAL_AGENT_TOKEN");
        boolean testProfile = Arrays.asList(environment.getActiveProfiles()).contains("test");
        if (!testProfile && !StringUtils.hasText(internalAgentToken)) {
            throw new IllegalStateException("GENIE_INTERNAL_AGENT_TOKEN must be configured outside the test profile");
        }
    }

    String internalAgentToken() {
        return internalAgentToken;
    }

    @Override public String toString() {
        return "SecurityProperties[internalAgentToken=redacted]";
    }
}
