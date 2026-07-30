package com.jd.genie.platform.user.config;

import com.jd.genie.platform.user.mapper.UserMapper;
import com.jd.genie.platform.user.service.AcceptanceUserSeeder;
import com.jd.genie.platform.user.service.TenantService;
import com.jd.genie.platform.user.service.UserService;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;

@Configuration
@Profile("mvp-acceptance")
public class AcceptanceUserSeedConfiguration {

    @Bean
    AcceptanceUserSeeder acceptanceUserSeeder(TenantService tenantService, UserService userService,
                                               UserMapper userMapper, Environment environment) {
        return new AcceptanceUserSeeder(tenantService, userService, userMapper, environment);
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    ApplicationRunner acceptanceUserSeedRunner(AcceptanceUserSeeder acceptanceUserSeeder) {
        return arguments -> acceptanceUserSeeder.seed();
    }
}
