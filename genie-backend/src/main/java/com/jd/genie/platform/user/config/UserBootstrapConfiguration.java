package com.jd.genie.platform.user.config;

import com.jd.genie.platform.user.service.BootstrapAdminService;
import java.time.Clock;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@EnableConfigurationProperties(BootstrapProperties.class)
public class UserBootstrapConfiguration {

    @Bean
    Clock userClock() {
        return Clock.systemUTC();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    @Profile("!mvp-acceptance")
    ApplicationRunner bootstrapAdminRunner(BootstrapAdminService bootstrapAdminService, Environment environment) {
        return arguments -> bootstrapAdminService.initialize(environment.matchesProfiles("prod"));
    }
}
