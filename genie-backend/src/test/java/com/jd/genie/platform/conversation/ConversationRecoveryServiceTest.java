package com.jd.genie.platform.conversation;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.conversation.entity.ConversationEntity;
import com.jd.genie.platform.conversation.entity.ConversationMessageEntity;
import com.jd.genie.platform.conversation.mapper.ConversationMapper;
import com.jd.genie.platform.conversation.mapper.ConversationMessageMapper;
import com.jd.genie.platform.conversation.service.ConversationRecoveryRunner;
import com.jd.genie.platform.conversation.service.ConversationRecoveryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Testcontainers
@SpringBootTest(classes = ConversationRecoveryServiceTest.TestConfig.class)
class ConversationRecoveryServiceTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
        .withDatabaseName("genie_mvp_b_recovery")
        .withUsername("genie")
        .withPassword("genie");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ConversationRecoveryService recoveryService;

    @Autowired
    private ConversationMapper conversationMapper;

    @Autowired
    private ConversationMessageMapper conversationMessageMapper;

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
        insertConversation("conv-a", "tenant-a", "owner-a", null);
        insertConversation("conv-b", "tenant-b", "owner-b", null);
        insertConversation("conv-deleted", "tenant-a", "owner-a", Instant.parse("2026-01-10T00:00:00Z"));
    }

    @Test
    void recoveryInterruptsPendingAndStreamingAssistantsAcrossTenantsInOneBatch() {
        insertMessage("msg-pending", "conv-a", 1L, "ASSISTANT", "PENDING", null, "req-pending", "{\"payloadVersion\":1}");
        insertMessage("msg-streaming", "conv-a", 2L, "ASSISTANT", "STREAMING", null, "req-streaming", "{\"payloadVersion\":1}");
        insertMessage("msg-other-tenant", "conv-b", 1L, "ASSISTANT", "STREAMING", null, "req-other", null);
        insertMessage("msg-deleted", "conv-deleted", 1L, "ASSISTANT", "PENDING", null, "req-deleted", null);
        insertMessage("msg-completed", "conv-a", 3L, "ASSISTANT", "COMPLETED", "done", "req-completed", null);
        insertMessage("msg-failed", "conv-a", 4L, "ASSISTANT", "FAILED", null, "req-failed", null);
        insertMessage("msg-interrupted", "conv-a", 5L, "ASSISTANT", "INTERRUPTED", null, "req-interrupted", null);
        insertMessage("msg-user-pending", "conv-a", 6L, "USER", "PENDING", "user", "req-user", null);
        Map<String, Object> before = message("msg-pending");

        int updated = recoveryService.recoverInterruptedAssistants();

        assertEquals(4, updated);
        assertRecovered("msg-pending", before);
        assertRecovered("msg-streaming", null);
        assertRecovered("msg-other-tenant", null);
        assertRecovered("msg-deleted", null);
        assertStatus("msg-completed", "COMPLETED");
        assertStatus("msg-failed", "FAILED");
        assertStatus("msg-interrupted", "INTERRUPTED");
        assertStatus("msg-user-pending", "PENDING");
        assertEquals("{\"payloadVersion\":1}", message("msg-pending").get("stream_snapshot"));
    }

    @Test
    void recoveryIsIdempotentAndDoesNotAdvanceVersionOrUpdatedAtAgain() {
        insertMessage("msg-once", "conv-a", 1L, "ASSISTANT", "STREAMING", null, "req-once", null);

        assertEquals(1, recoveryService.recoverInterruptedAssistants());
        Map<String, Object> once = message("msg-once");
        assertEquals(0, recoveryService.recoverInterruptedAssistants());
        Map<String, Object> twice = message("msg-once");

        assertEquals(once.get("status"), twice.get("status"));
        assertEquals(once.get("error_code"), twice.get("error_code"));
        assertEquals(once.get("error_message"), twice.get("error_message"));
        assertEquals(once.get("version"), twice.get("version"));
        assertEquals(once.get("updated_at"), twice.get("updated_at"));
    }

    @Test
    void recoveredInterruptedMessagesDoNotEnterCompletedHistory() {
        insertMessage("msg-user-1", "conv-a", 1L, "USER", "COMPLETED", "u1", "req-1", null);
        insertMessage("msg-assistant-1", "conv-a", 1L, "ASSISTANT", "STREAMING", null, "req-1", null);

        assertEquals(1, recoveryService.recoverInterruptedAssistants());

        assertEquals(0, conversationMessageMapper.selectCompletedHistoryTurns(
            "tenant-a", "owner-a", "conv-a", null, 6).size());
    }

    @Test
    void runnerPropagatesRecoveryFailureSoApplicationReadyIsBlocked() throws Exception {
        ConversationRecoveryService failingRecovery = mock(ConversationRecoveryService.class);
        when(failingRecovery.recoverInterruptedAssistants())
            .thenThrow(new TransientDataAccessResourceException("startup recovery failed"));
        ConversationRecoveryRunner runner = new ConversationRecoveryRunner(failingRecovery);

        assertThrows(TransientDataAccessResourceException.class, () -> runner.run(null));
    }

    private void assertRecovered(String messageId, Map<String, Object> before) {
        Map<String, Object> row = message(messageId);
        assertEquals("INTERRUPTED", row.get("status"));
        assertEquals(MvpErrorCode.SERVICE_RESTARTED.name(), row.get("error_code"));
        assertNull(row.get("error_message"));
        assertEquals(1L, ((Number) row.get("version")).longValue());
        assertNotNull(row.get("updated_at"));
        if (before != null) {
            assertNotEquals(before.get("updated_at"), row.get("updated_at"));
        }
    }

    private void assertStatus(String messageId, String status) {
        assertEquals(status, message(messageId).get("status"));
    }

    private Map<String, Object> message(String messageId) {
        return jdbcTemplate.queryForMap(
            "SELECT status, error_code, error_message, stream_snapshot, updated_at, version FROM conversation_message WHERE id = ?",
            messageId
        );
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

    private void insertTenantAndUser(String tenantId, String userId) {
        jdbcTemplate.update("""
            INSERT INTO app_tenant(id, code, name, status, created_at, updated_at)
            VALUES (?, ?, ?, 'ACTIVE', NOW(6), NOW(6))
            """, tenantId, tenantId, tenantId);
        jdbcTemplate.update("""
            INSERT INTO app_user(id, tenant_id, username, display_name, password_hash, role, status, created_at, updated_at)
            VALUES (?, ?, ?, ?, 'hash', 'USER', 'ACTIVE', NOW(6), NOW(6))
            """, userId, tenantId, userId, userId);
    }

    private void insertConversation(String id, String tenantId, String ownerId, Instant deletedAt) {
        ConversationEntity conversation = new ConversationEntity();
        conversation.setId(id);
        conversation.setTenantId(tenantId);
        conversation.setOwnerId(ownerId);
        conversation.setTitle("新对话");
        conversation.setNextTurnNo(1L);
        conversation.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        conversation.setUpdatedAt(conversation.getCreatedAt());
        conversation.setDeletedAt(deletedAt);
        assertEquals(1, conversationMapper.insert(conversation));
    }

    private void insertMessage(String id, String conversationId, long turnNo, String role, String status,
                               String content, String requestId, String snapshot) {
        ConversationMessageEntity message = new ConversationMessageEntity();
        message.setId(id);
        message.setConversationId(conversationId);
        message.setTurnNo(turnNo);
        message.setRole(role);
        message.setStatus(status);
        message.setRequestId(requestId);
        message.setContent(content);
        message.setStreamSnapshot(snapshot);
        message.setCreatedAt(Instant.parse("2026-02-01T00:00:00Z").plusSeconds(turnNo));
        message.setUpdatedAt(message.getCreatedAt());
        assertEquals(1, conversationMessageMapper.insert(message));
    }

    @SpringBootConfiguration
    @Import({ConversationRecoveryService.class})
    @ImportAutoConfiguration({
        DataSourceAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class,
        JdbcTemplateAutoConfiguration.class,
        TransactionAutoConfiguration.class,
        MybatisPlusAutoConfiguration.class
    })
    @MapperScan("com.jd.genie.platform.conversation.mapper")
    static class TestConfig {
    }
}