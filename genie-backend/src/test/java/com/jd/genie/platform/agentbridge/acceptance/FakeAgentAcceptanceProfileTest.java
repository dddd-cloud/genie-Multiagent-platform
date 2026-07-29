package com.jd.genie.platform.agentbridge.acceptance;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FakeAgentAcceptanceProfileTest {

    @Test
    void productionProfileDoesNotRegisterTheFakeAgentFilter() {
        try (AnnotationConfigApplicationContext context = contextWithProfile("prod")) {
            assertFalse(context.containsBean("fakeAgentAcceptanceFilter"));
            assertTrueNoFakeFilter(context);
        }
    }

    @Test
    void acceptanceProfileRegistersTheFakeAgentFilter() {
        try (AnnotationConfigApplicationContext context = contextWithProfile("mvp-acceptance")) {
            assertNotNull(context.getBean(FakeAgentAcceptanceFilter.class));
        }
    }

    private AnnotationConfigApplicationContext contextWithProfile(String profile) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().setActiveProfiles(profile);
        context.register(FakeAgentAcceptanceFilter.class);
        context.refresh();
        return context;
    }

    private void assertTrueNoFakeFilter(AnnotationConfigApplicationContext context) {
        assertTrue(context.getBeansOfType(FakeAgentAcceptanceFilter.class).isEmpty());
    }
}
