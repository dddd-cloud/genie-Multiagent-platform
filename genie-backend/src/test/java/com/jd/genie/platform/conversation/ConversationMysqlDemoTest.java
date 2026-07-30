package com.jd.genie.platform.conversation;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.CurrentUserProvider;
import com.jd.genie.platform.contract.UserRole;
import com.jd.genie.platform.contract.support.FakeCurrentUserProvider;
import com.jd.genie.platform.conversation.controller.ConversationController;
import com.jd.genie.platform.conversation.entity.ConversationEntity;
import com.jd.genie.platform.conversation.exception.ConversationExceptionHandler;
import com.jd.genie.platform.conversation.mapper.ConversationMapper;
import com.jd.genie.platform.conversation.mapper.ConversationMessageMapper;
import com.jd.genie.platform.conversation.service.ConversationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("conversation-test")
@SpringBootTest(classes = ConversationMysqlDemoTest.TestConfig.class)
class ConversationMysqlDemoTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
        .withDatabaseName("genie_mvp_b_demo");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ConversationMapper conversationMapper;

    @Autowired
    private FakeCurrentUserProvider currentUserProvider;

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
    }

    @BeforeEach
    void setUpSchema() {
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS=0");
        jdbcTemplate.execute("DROP TABLE IF EXISTS conversation_message");
        jdbcTemplate.execute("DROP TABLE IF EXISTS conversation");
        jdbcTemplate.execute("DROP TABLE IF EXISTS app_user");
        jdbcTemplate.execute("DROP TABLE IF EXISTS app_tenant");
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS=1");
        jdbcTemplate.execute("""
            CREATE TABLE app_tenant (
                id VARCHAR(36) NOT NULL,
                code VARCHAR(64) NOT NULL,
                name VARCHAR(100) NOT NULL,
                status VARCHAR(20) NOT NULL,
                created_at DATETIME(6) NOT NULL,
                updated_at DATETIME(6) NOT NULL,
                PRIMARY KEY (id),
                UNIQUE KEY uk_tenant_code (code)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);
        jdbcTemplate.execute("""
            CREATE TABLE app_user (
                id VARCHAR(36) NOT NULL,
                tenant_id VARCHAR(36) NOT NULL,
                username VARCHAR(64) NOT NULL,
                display_name VARCHAR(100) NOT NULL,
                password_hash VARCHAR(255) NOT NULL,
                role VARCHAR(20) NOT NULL,
                status VARCHAR(20) NOT NULL,
                created_at DATETIME(6) NOT NULL,
                updated_at DATETIME(6) NOT NULL,
                PRIMARY KEY (id),
                UNIQUE KEY uk_user_tenant_username (tenant_id, username),
                KEY idx_user_tenant_status (tenant_id, status),
                CONSTRAINT fk_user_tenant FOREIGN KEY (tenant_id) REFERENCES app_tenant(id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);
        for (String statement : loadV003Statements()) {
            jdbcTemplate.execute(statement);
        }
        insertTenantAndUser("tenant-a", "owner-a");
        insertTenantAndUser("tenant-b", "owner-b");
        insertUser("tenant-a", "owner-other");
        currentUserProvider.setCurrentUser(user("tenant-a", "owner-a"));
    }

    @Test
    void fiveMinuteConversationCrudIsolationAndSoftDeleteDemo() throws Exception {
        System.out.println("[DEMO] [1/6] Testcontainers has started real MySQL image mysql:8.0.36");

        String firstResponse = mockMvc.perform(post("/api/v1/conversations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Demo Conversation\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.data.title").value("Demo Conversation"))
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
        String conversationId = objectMapper.readTree(firstResponse).get("data").get("id").asText();
        System.out.println("[DEMO] [2/6] Created conversationId=" + conversationId
            + " tenant=tenant-a owner=owner-a");

        mockMvc.perform(post("/api/v1/conversations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Second Conversation\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OK"));
        mockMvc.perform(get("/api/v1/conversations")
                .param("page", "1")
                .param("pageSize", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items", hasSize(1)))
            .andExpect(jsonPath("$.data.hasMore").value(true));
        mockMvc.perform(get("/api/v1/conversations/" + conversationId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(conversationId));
        System.out.println("[DEMO] [3/6] Pagination and detail query passed; hasMore=true with pageSize=1");

        mockMvc.perform(patch("/api/v1/conversations/" + conversationId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Renamed Demo Conversation\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.title").value("Renamed Demo Conversation"));
        System.out.println("[DEMO] [4/6] Rename passed: Demo Conversation -> Renamed Demo Conversation");

        currentUserProvider.setCurrentUser(user("tenant-b", "owner-b"));
        mockMvc.perform(get("/api/v1/conversations/" + conversationId))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        currentUserProvider.setCurrentUser(user("tenant-a", "owner-other"));
        mockMvc.perform(get("/api/v1/conversations/" + conversationId))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        System.out.println("[DEMO] [5/6] Tenant and owner isolation passed: other users see RESOURCE_NOT_FOUND");

        currentUserProvider.setCurrentUser(user("tenant-a", "owner-a"));
        mockMvc.perform(delete("/api/v1/conversations/" + conversationId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.data").isEmpty());
        mockMvc.perform(get("/api/v1/conversations/" + conversationId))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        LocalDateTime deletedAt = jdbcTemplate.queryForObject(
            "SELECT deleted_at FROM conversation WHERE id = ?",
            LocalDateTime.class,
            conversationId
        );
        assertNotNull(deletedAt);
        assertEquals(1, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM conversation WHERE id = ?",
            Integer.class,
            conversationId
        ));
        System.out.println("[DEMO] [6/6] Soft delete passed: business query hidden, DB row retained, deleted_at="
            + deletedAt);
    }

    private List<String> loadV003Statements() {
        return List.of(readResource("db/migration/V003__conversation.sql").split(";"))
            .stream()
            .map(String::trim)
            .filter(statement -> !statement.isEmpty())
            .toList();
    }

    private String readResource(String path) {
        try (var input = Thread.currentThread().getContextClassLoader().getResourceAsStream(path)) {
            assertNotNull(input, "Missing resource: " + path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read resource: " + path, e);
        }
    }

    private CurrentUser user(String tenantId, String userId) {
        return new CurrentUser(tenantId, userId, userId, userId, UserRole.USER);
    }

    private void insertTenantAndUser(String tenantId, String userId) {
        jdbcTemplate.update("""
            INSERT INTO app_tenant(id, code, name, status, created_at, updated_at)
            VALUES (?, ?, ?, 'ACTIVE', NOW(6), NOW(6))
            """, tenantId, tenantId, tenantId);
        insertUser(tenantId, userId);
    }

    private void insertUser(String tenantId, String userId) {
        jdbcTemplate.update("""
            INSERT INTO app_user(id, tenant_id, username, display_name, password_hash, role, status, created_at, updated_at)
            VALUES (?, ?, ?, ?, 'hash', 'USER', 'ACTIVE', NOW(6), NOW(6))
            """, userId, tenantId, userId, userId);
    }

    @Profile("conversation-test")
    @Configuration
    @Import({
        ConversationController.class,
        ConversationService.class,
        ConversationExceptionHandler.class
    })
    @ImportAutoConfiguration(classes = {
        DispatcherServletAutoConfiguration.class,
        WebMvcAutoConfiguration.class,
        HttpMessageConvertersAutoConfiguration.class,
        JacksonAutoConfiguration.class,
        DataSourceAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class,
        JdbcTemplateAutoConfiguration.class,
        MybatisPlusAutoConfiguration.class
    }, exclude = {
        SecurityAutoConfiguration.class,
        SecurityFilterAutoConfiguration.class,
        UserDetailsServiceAutoConfiguration.class
    })
    @MapperScan("com.jd.genie.platform.conversation.mapper")
    static class TestConfig {
        @Bean
        FakeCurrentUserProvider currentUserProvider() {
            return new FakeCurrentUserProvider();
        }
    }
}
