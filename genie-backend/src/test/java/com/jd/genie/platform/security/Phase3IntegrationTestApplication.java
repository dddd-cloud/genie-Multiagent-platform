package com.jd.genie.platform.security;

import com.jd.genie.GenieApplication;
import com.jd.genie.config.DataAgentInitRunner;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * Test-only application root for phase 3 integration tests.  It loads the real application
 * components while excluding the unrelated DataAgent startup runner and its external datasource.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@MapperScan("com.jd.genie.mapper")
@ComponentScan(basePackages = "com.jd.genie", excludeFilters = {
    @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = {GenieApplication.class, DataAgentInitRunner.class}
    ),
    @ComponentScan.Filter(
        type = FilterType.CUSTOM,
        classes = ConversationTestConfigurationExcludeFilter.class
    )
})
class Phase3IntegrationTestApplication {
}
