package com.jd.genie.platform.phase2.configuration.support;

import org.junit.jupiter.api.BeforeEach;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;

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
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan({
        "com.jd.genie.platform.phase2.configuration.agent.mapper",
        "com.jd.genie.platform.phase2.configuration.skill.mapper"
    })
    static class TestApplication {
    }
}