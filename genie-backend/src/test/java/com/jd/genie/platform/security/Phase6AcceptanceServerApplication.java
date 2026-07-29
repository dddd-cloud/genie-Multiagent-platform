package com.jd.genie.platform.security;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;

/**
 * Test-only persistent HTTP application for the Phase 6 acceptance scripts.
 *
 * <p>It reuses {@link Phase3IntegrationTestApplication} as the sole application root.</p>
 */
public class Phase6AcceptanceServerApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(Phase3IntegrationTestApplication.class);
        application.setWebApplicationType(WebApplicationType.SERVLET);
        application.run(args);
    }
}
