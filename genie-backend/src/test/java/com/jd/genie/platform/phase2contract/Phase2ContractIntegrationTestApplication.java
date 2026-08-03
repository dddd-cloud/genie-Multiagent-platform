package com.jd.genie.platform.phase2contract;

import com.jd.genie.GenieApplication;
import com.jd.genie.config.DataAgentInitRunner;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * Public test application root for Phase2 C0 security/contract integration tests.
 * Mirrors the existing phase3 test bootstrap without depending on package-private types.
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
        classes = Phase2ConversationTestConfigurationExcludeFilter.class
    )
})
public class Phase2ContractIntegrationTestApplication {
}
