package com.jd.genie.platform.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InternalAgentConfigurationTest {
    @Test void testProfileAllowsMissingTokenAndExplicitTokenRemainsPrivate() {
        MockEnvironment missing = new MockEnvironment(); missing.setActiveProfiles("test");
        assertDoesNotThrow(() -> new SecurityProperties(missing));
        MockEnvironment configured = new MockEnvironment(); configured.setActiveProfiles("test"); configured.setProperty("GENIE_INTERNAL_AGENT_TOKEN", "local-test-token");
        assertFalse(new SecurityProperties(configured).toString().contains("local-test-token"));
    }

    @Test void nonTestProfileRejectsMissingOrBlankTokenWithoutExposingValues() {
        IllegalStateException missing = assertThrows(IllegalStateException.class, () -> new SecurityProperties(new MockEnvironment()));
        MockEnvironment blank = new MockEnvironment(); blank.setProperty("GENIE_INTERNAL_AGENT_TOKEN", "   ");
        IllegalStateException blankFailure = assertThrows(IllegalStateException.class, () -> new SecurityProperties(blank));
        assertFalse(missing.getMessage().contains("token="));
        assertFalse(blankFailure.getMessage().contains("   "));
    }

    @Test void nonTestProfileAcceptsConfiguredToken() {
        MockEnvironment configured = new MockEnvironment(); configured.setProperty("GENIE_INTERNAL_AGENT_TOKEN", "local-test-token");
        assertDoesNotThrow(() -> new SecurityProperties(configured));
    }
}
