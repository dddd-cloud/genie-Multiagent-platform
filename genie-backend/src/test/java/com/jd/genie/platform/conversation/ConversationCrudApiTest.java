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
import com.jd.genie.platform.conversation.entity.ConversationMessageEntity;
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
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("conversation-test")
@SpringBootTest(classes = ConversationCrudApiTest.TestConfig.class)
class ConversationCrudApiTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
        .withDatabaseName("genie_mvp_b_crud")
        .withUsername("genie")
        .withPassword("genie");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ConversationMapper conversationMapper;

    @Autowired
    private ConversationMessageMapper conversationMessageMapper;

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
    void createConversationUsesCurrentUserDefaultTitleNextTurnAndUtcTimestamps() throws Exception {
        String response = mockMvc.perform(post("/api/v1/conversations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.data.title").value("新对话"))
            .andExpect(jsonPath("$.data.privacyMode").value(false))
            .andExpect(jsonPath("$.data.createdAt").value(not("")))
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

        String conversationId = objectMapper.readTree(response).get("data").get("id").asText();
        Map<String, Object> stored = jdbcTemplate.queryForMap(
            "SELECT tenant_id, owner_id, next_turn_no, created_at, updated_at FROM conversation WHERE id = ?",
            conversationId);
        assertEquals("tenant-a", stored.get("tenant_id"));
        assertEquals("owner-a", stored.get("owner_id"));
        assertEquals(1L, ((Number) stored.get("next_turn_no")).longValue());
        assertTrue(objectMapper.readTree(response).get("data").get("createdAt").asText().endsWith("Z"));

        mockMvc.perform(post("/api/v1/conversations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"" + "x".repeat(201) + "\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void createConversationCanEnablePrivacyMode() throws Exception {
        String response = mockMvc.perform(post("/api/v1/conversations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"privacyMode\":true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.privacyMode").value(true))
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

        String conversationId = objectMapper.readTree(response).get("data").get("id").asText();
        Number privacyMode = jdbcTemplate.queryForObject(
            "SELECT privacy_mode FROM conversation WHERE id = ?", Number.class, conversationId);
        assertEquals(1, privacyMode.intValue());
    }

    @Test
    void patchConversationCanTogglePrivacyMode() throws Exception {
        String response = mockMvc.perform(post("/api/v1/conversations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
        String conversationId = objectMapper.readTree(response).get("data").get("id").asText();

        mockMvc.perform(patch("/api/v1/conversations/" + conversationId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"privacyMode\":true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.privacyMode").value(true));

        Number privacyMode = jdbcTemplate.queryForObject(
            "SELECT privacy_mode FROM conversation WHERE id = ?", Number.class, conversationId);
        assertEquals(1, privacyMode.intValue());
    }

    @Test
    void listConversationsUsesContractPagingOrderingBatchPreviewAndIsolation() throws Exception {
        insertConversation("conv-old", "tenant-a", "owner-a", "Old", Instant.parse("2026-01-01T00:00:00Z"), null);
        insertConversation("conv-recent", "tenant-a", "owner-a", "Recent", Instant.parse("2026-01-03T00:00:00Z"), null);
        insertConversation("conv-null", "tenant-a", "owner-a", "Null", null, null);
        insertConversation("conv-other", "tenant-a", "owner-other", "Other", Instant.parse("2026-01-04T00:00:00Z"), null);
        insertUserMessage("msg-recent", "conv-recent", 1L,
            "第一行\n第二行  " + "你".repeat(79) + "🙂tail", "req-recent");
        insertUserMessage("msg-old", "conv-old", 1L, "old preview", "req-old");

        String response = mockMvc.perform(get("/api/v1/conversations")
                .param("page", "1")
                .param("pageSize", "2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.page").value(1))
            .andExpect(jsonPath("$.data.pageSize").value(2))
            .andExpect(jsonPath("$.data.hasMore").value(true))
            .andExpect(jsonPath("$.data.total").doesNotExist())
            .andExpect(jsonPath("$.data.items", hasSize(2)))
            .andExpect(jsonPath("$.data.items[0].id").value("conv-recent"))
            .andExpect(jsonPath("$.data.items[1].id").value("conv-old"))
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

        JsonNode first = objectMapper.readTree(response).get("data").get("items").get(0);
        String preview = first.get("lastMessagePreview").asText();
        assertEquals(80, preview.codePointCount(0, preview.length()));
        assertEquals("第一行 第二行", preview.substring(0, "第一行 第二行".length()));

        mockMvc.perform(get("/api/v1/conversations").param("page", "0").param("pageSize", "20"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void detailRenameAndMessagesHideCrossTenantCrossOwnerAndDeletedConversations() throws Exception {
        insertConversation("conv-owned", "tenant-a", "owner-a", "Owned", null, null);
        insertConversation("conv-other-owner", "tenant-a", "owner-other", "Other", null, null);
        insertConversation("conv-other-tenant", "tenant-b", "owner-b", "Tenant", null, null);
        insertConversation("conv-deleted", "tenant-a", "owner-a", "Deleted", null, Instant.parse("2026-01-01T00:00:00Z"));
        for (int turn = 1; turn <= 55; turn++) {
            insertUserMessage("msg-user-" + turn, "conv-owned", turn, "user-" + turn, "req-user-" + turn);
            insertAssistantMessage("msg-assistant-" + turn, "conv-owned", turn, "COMPLETED", "assistant-" + turn,
                "req-assistant-" + turn);
        }

        mockMvc.perform(get("/api/v1/conversations/conv-owned"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value("conv-owned"));
        mockMvc.perform(get("/api/v1/conversations/conv-other-owner"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/conversations/conv-other-tenant"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/conversations/conv-deleted/messages"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        String historyResponse = mockMvc.perform(get("/api/v1/conversations/conv-owned/messages"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(100)))
            .andExpect(jsonPath("$.data[0].turnNo").value(6))
            .andExpect(jsonPath("$.data[0].role").value("USER"))
            .andExpect(jsonPath("$.data[1].role").value("ASSISTANT"))
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
        JsonNode history = objectMapper.readTree(historyResponse).get("data");
        assertEquals(55, history.get(99).get("turnNo").asInt());
        assertTrue(history.get(1).get("deepThink").isNull());
        assertTrue(history.get(1).get("outputStyle").isNull());

        mockMvc.perform(patch("/api/v1/conversations/conv-owned")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\" Renamed \"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.title").value("Renamed"));
        mockMvc.perform(patch("/api/v1/conversations/conv-owned")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"   \"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void softDeleteLocksOwnedConversationRejectsActiveAssistantAndKeepsMessages() throws Exception {
        insertConversation("conv-busy", "tenant-a", "owner-a", "Busy", null, null);
        insertAssistantMessage("msg-busy", "conv-busy", 1L, "STREAMING", null, "req-busy");

        mockMvc.perform(delete("/api/v1/conversations/conv-busy"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CONVERSATION_BUSY"));
        assertNull(conversationMapper.selectOwnedConversationForUpdate("tenant-a", "owner-a", "missing"));
        assertNotNull(conversationMapper.selectOwnedConversationForUpdate("tenant-a", "owner-a", "conv-busy"));

        insertConversation("conv-done", "tenant-a", "owner-a", "Done", null, null);
        insertAssistantMessage("msg-done", "conv-done", 1L, "COMPLETED", "done", "req-done");

        mockMvc.perform(delete("/api/v1/conversations/conv-done"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.data").isEmpty());

        assertEquals(1, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM conversation_message WHERE conversation_id = 'conv-done'", Integer.class));
        assertNotNull(jdbcTemplate.queryForObject(
            "SELECT deleted_at FROM conversation WHERE id = 'conv-done'", Object.class));
        mockMvc.perform(get("/api/v1/conversations/conv-done"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(patch("/api/v1/conversations/conv-done")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"after delete\"}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(delete("/api/v1/conversations/conv-done"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/conversations/conv-done/messages"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    private List<String> loadV003Statements() {
        return ConversationSchemaStatements.load();
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

    private void insertConversation(String id, String tenantId, String ownerId, String title,
                                    Instant lastMessageAt, Instant deletedAt) {
        ConversationEntity conversation = new ConversationEntity();
        conversation.setId(id);
        conversation.setTenantId(tenantId);
        conversation.setOwnerId(ownerId);
        conversation.setTitle(title);
        conversation.setPrivacyMode(false);
        conversation.setLastMessageAt(lastMessageAt);
        conversation.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z").plusSeconds(Math.abs(id.hashCode() % 1000)));
        conversation.setUpdatedAt(conversation.getCreatedAt());
        conversation.setDeletedAt(deletedAt);
        assertEquals(1, conversationMapper.insert(conversation));
    }

    private void insertUserMessage(String id, String conversationId, long turnNo, String content, String requestId) {
        insertMessage(id, conversationId, turnNo, "USER", "COMPLETED", content, requestId);
    }

    private void insertAssistantMessage(String id, String conversationId, long turnNo, String status,
                                        String content, String requestId) {
        insertMessage(id, conversationId, turnNo, "ASSISTANT", status, content, requestId);
    }

    private void insertMessage(String id, String conversationId, long turnNo, String role, String status,
                               String content, String requestId) {
        ConversationMessageEntity message = new ConversationMessageEntity();
        message.setId(id);
        message.setConversationId(conversationId);
        message.setTurnNo(turnNo);
        message.setRole(role);
        message.setStatus(status);
        message.setRequestId(requestId);
        message.setContent(content);
        message.setCreatedAt(Instant.parse("2026-02-01T00:00:00Z").plusSeconds(turnNo));
        message.setUpdatedAt(message.getCreatedAt());
        assertEquals(1, conversationMessageMapper.insert(message));
    }

    @Profile("conversation-test")
    @Configuration
    @Import({
        ConversationController.class,
        com.jd.genie.platform.conversation.exception.ConversationExceptionHandler.class,
        ConversationService.class
    })
    @ImportAutoConfiguration({
        DispatcherServletAutoConfiguration.class,
        WebMvcAutoConfiguration.class,
        HttpMessageConvertersAutoConfiguration.class,
        JacksonAutoConfiguration.class,
        DataSourceAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class,
        JdbcTemplateAutoConfiguration.class,
        MybatisPlusAutoConfiguration.class
    })
    @MapperScan("com.jd.genie.platform.conversation.mapper")
    static class TestConfig {
        @Bean
        FakeCurrentUserProvider currentUserProvider() {
            return new FakeCurrentUserProvider();
        }
    }
}
