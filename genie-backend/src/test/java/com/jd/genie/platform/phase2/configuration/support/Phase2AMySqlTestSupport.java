package com.jd.genie.platform.phase2.configuration.support;

import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.UserRole;
import com.jd.genie.agent.llm.LLMSettings;
import com.jd.genie.config.GenieConfig;
import com.jd.genie.platform.phase2.configuration.agent.runtime.AgentRuntimeCatalogService;
import com.jd.genie.platform.phase2.configuration.agent.service.AgentDefinitionService;
import com.jd.genie.platform.phase2.configuration.model.ModelCatalogService;
import com.jd.genie.platform.phase2.configuration.prompt.AgentPromptCompiler;
import com.jd.genie.platform.phase2.configuration.prompt.PromptPreviewService;
import com.jd.genie.platform.phase2.configuration.skill.service.SkillDefinitionService;
import com.jd.genie.platform.phase2contract.port.ToolBindingPort;
import com.jd.genie.platform.phase2contract.support.FakeToolBindingPort;
import org.junit.jupiter.api.BeforeEach;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.Map;

@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(classes = Phase2AMySqlTestSupport.TestApplication.class, properties = {
    "spring.flyway.locations=classpath:db/migration",
    "spring.flyway.clean-disabled=true"
})
public abstract class Phase2AMySqlTestSupport {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
        .withDatabaseName("genie_phase2_a_mapper")
        .withUsername("genie")
        .withPassword("genie")
        .withStartupTimeout(Duration.ofMinutes(3));

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected FakeToolBindingPort fakeToolBindingPort;

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
    }

    @BeforeEach
    void clearPhase2ATables() {
        jdbcTemplate.update("DELETE FROM agent_skill_binding");
        jdbcTemplate.update("DELETE FROM agent_definition");
        jdbcTemplate.update("DELETE FROM skill_definition");
        fakeToolBindingPort.reset();
    }

    protected CurrentUser userA() {
        return new CurrentUser("tenant-a", "owner-a", "owner-a", "Owner A", UserRole.USER);
    }

    protected CurrentUser userB() {
        return new CurrentUser("tenant-a", "owner-b", "owner-b", "Owner B", UserRole.USER);
    }

    protected CurrentUser tenantBUser() {
        return new CurrentUser("tenant-b", "owner-a", "owner-a", "Tenant B", UserRole.USER);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({
        AgentDefinitionService.class,
        AgentRuntimeCatalogService.class,
        SkillDefinitionService.class,
        AgentPromptCompiler.class,
        ModelCatalogService.class,
        PromptPreviewService.class
    })
    @MapperScan({
        "com.jd.genie.platform.phase2.configuration.agent.mapper",
        "com.jd.genie.platform.phase2.configuration.skill.mapper"
    })
    static class TestApplication {
        @Bean
        ToolBindingPort toolBindingPort() {
            return new FakeToolBindingPort();
        }

        @Bean
        GenieConfig genieConfig() {
            return new GenieConfig() {
                @Override
                public Map<String, LLMSettings> getLlmSettingsMap() {
                    return Map.of(
                        "qwen-plus", LLMSettings.builder().model("qwen-plus").apiKey("SECRET_MARKER").baseUrl("https://secret.example").interfaceUrl("/private").build(),
                        "qwen-max", LLMSettings.builder().model("qwen-max").build()
                    );
                }

                @Override
                public String getReactModelName() {
                    return "qwen-plus";
                }
            };
        }
    }
}
