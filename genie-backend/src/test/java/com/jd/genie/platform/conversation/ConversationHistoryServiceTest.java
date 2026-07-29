package com.jd.genie.platform.conversation;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.jd.genie.platform.contract.ConversationHistoryItem;
import com.jd.genie.platform.contract.ConversationMessageRole;
import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.contract.UserRole;
import com.jd.genie.platform.conversation.entity.ConversationEntity;
import com.jd.genie.platform.conversation.entity.ConversationMessageEntity;
import com.jd.genie.platform.conversation.exception.ConversationException;
import com.jd.genie.platform.conversation.mapper.ConversationMapper;
import com.jd.genie.platform.conversation.mapper.ConversationMessageMapper;
import com.jd.genie.platform.conversation.service.ConversationExecutionService;
import com.jd.genie.platform.conversation.service.ConversationHistoryService;
import com.jd.genie.platform.conversation.service.ConversationTitleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testcontainers
@ActiveProfiles("conversation-test")
@SpringBootTest(classes = ConversationHistoryServiceTest.TestConfig.class)
class ConversationHistoryServiceTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
        .withDatabaseName("genie_mvp_b_history")
        .withUsername("genie")
        .withPassword("genie");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ConversationHistoryService historyService;

    @Autowired
    private ConversationExecutionService executionService;

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
        insertUser("tenant-a", "owner-other");
    }

    @Test
    void loadsCompletedTurnsInAscendingMessageOrder() {
        insertConversation("conv-basic", "tenant-a", "owner-a", null);
        insertCompletedTurn("conv-basic", 1L, "req-1", "u1", "a1");
        insertCompletedTurn("conv-basic", 2L, "req-2", "u2", "a2");
        insertCompletedTurn("conv-basic", 3L, "req-3", "u3", "a3");

        List<ConversationHistoryItem> history = historyService.loadCompletedHistory(
            user("tenant-a", "owner-a"), "conv-basic", null, 6, 100);

        assertHistory(history, List.of(
            item(1L, ConversationMessageRole.USER, "u1"),
            item(1L, ConversationMessageRole.ASSISTANT, "a1"),
            item(2L, ConversationMessageRole.USER, "u2"),
            item(2L, ConversationMessageRole.ASSISTANT, "a2"),
            item(3L, ConversationMessageRole.USER, "u3"),
            item(3L, ConversationMessageRole.ASSISTANT, "a3")
        ));
    }

    @Test
    void filtersIncompleteNonCompletedAndMismatchedTurns() {
        insertConversation("conv-filter", "tenant-a", "owner-a", null);
        insertCompletedTurn("conv-filter", 1L, "req-ok", "u-ok", "a-ok");
        insertMessage("missing-assistant-user", "conv-filter", 2L, "USER", "COMPLETED", "missing-a", "req-missing-a");
        insertMessage("missing-user-assistant", "conv-filter", 3L, "ASSISTANT", "COMPLETED", "missing-u", "req-missing-u");
        insertTurn("conv-filter", 4L, "req-pending", "COMPLETED", "PENDING", "u-pending", "a-pending");
        insertTurn("conv-filter", 5L, "req-streaming", "COMPLETED", "STREAMING", "u-streaming", "a-streaming");
        insertTurn("conv-filter", 6L, "req-failed", "COMPLETED", "FAILED", "u-failed", "a-failed");
        insertTurn("conv-filter", 7L, "req-interrupted", "COMPLETED", "INTERRUPTED", "u-interrupted", "a-interrupted");
        insertTurn("conv-filter", 8L, "req-user-pending", "PENDING", "COMPLETED", "u-pending", "a-user-pending");
        insertMessage("mismatch-user", "conv-filter", 9L, "USER", "COMPLETED", "mismatch-u", "req-user");
        insertMessage("mismatch-assistant", "conv-filter", 9L, "ASSISTANT", "COMPLETED", "mismatch-a", "req-assistant");

        List<ConversationHistoryItem> history = historyService.loadCompletedHistory(
            user("tenant-a", "owner-a"), "conv-filter", null, 10, 1000);

        assertHistory(history, List.of(
            item(1L, ConversationMessageRole.USER, "u-ok"),
            item(1L, ConversationMessageRole.ASSISTANT, "a-ok")
        ));
    }

    @Test
    void excludesCurrentRequestIdWithoutHalfTurn() {
        insertConversation("conv-exclude", "tenant-a", "owner-a", null);
        insertCompletedTurn("conv-exclude", 1L, "req-1", "u1", "a1");
        insertCompletedTurn("conv-exclude", 2L, "req-current", "u2", "a2");
        insertCompletedTurn("conv-exclude", 3L, "req-3", "u3", "a3");

        List<ConversationHistoryItem> history = executionService.loadCompletedHistory(
            user("tenant-a", "owner-a"), "conv-exclude", "req-current", 6, 100);

        assertHistory(history, List.of(
            item(1L, ConversationMessageRole.USER, "u1"),
            item(1L, ConversationMessageRole.ASSISTANT, "a1"),
            item(3L, ConversationMessageRole.USER, "u3"),
            item(3L, ConversationMessageRole.ASSISTANT, "a3")
        ));
    }

    @Test
    void appliesMaxTurnsToLatestCompletedTurnsThenReturnsAscending() {
        insertConversation("conv-turns", "tenant-a", "owner-a", null);
        insertCompletedTurn("conv-turns", 1L, "req-1", "u1", "a1");
        insertCompletedTurn("conv-turns", 2L, "req-2", "u2", "a2");
        insertCompletedTurn("conv-turns", 3L, "req-3", "u3", "a3");
        insertCompletedTurn("conv-turns", 4L, "req-4", "u4", "a4");

        assertHistory(historyService.loadCompletedHistory(user("tenant-a", "owner-a"), "conv-turns", null, 2, 100), List.of(
            item(3L, ConversationMessageRole.USER, "u3"),
            item(3L, ConversationMessageRole.ASSISTANT, "a3"),
            item(4L, ConversationMessageRole.USER, "u4"),
            item(4L, ConversationMessageRole.ASSISTANT, "a4")
        ));
        assertHistory(historyService.loadCompletedHistory(user("tenant-a", "owner-a"), "conv-turns", null, 1, 100), List.of(
            item(4L, ConversationMessageRole.USER, "u4"),
            item(4L, ConversationMessageRole.ASSISTANT, "a4")
        ));
        assertEquals(8, historyService.loadCompletedHistory(user("tenant-a", "owner-a"), "conv-turns", null, 10, 100).size());
        assertConversationError(MvpErrorCode.VALIDATION_ERROR,
            () -> historyService.loadCompletedHistory(user("tenant-a", "owner-a"), "conv-turns", null, 0, 100));
    }

    @Test
    void appliesMaxCharactersWithStringLengthWithoutSkippingOrTruncating() {
        insertConversation("conv-chars", "tenant-a", "owner-a", null);
        insertCompletedTurn("conv-chars", 1L, "req-1", "old", "short");
        insertCompletedTurn("conv-chars", 2L, "req-2", "中文", "🙂");
        insertCompletedTurn("conv-chars", 3L, "req-3", "abc", "de");

        int latestLength = "abc".length() + "de".length();
        assertHistory(historyService.loadCompletedHistory(user("tenant-a", "owner-a"), "conv-chars", null, 6, latestLength), List.of(
            item(3L, ConversationMessageRole.USER, "abc"),
            item(3L, ConversationMessageRole.ASSISTANT, "de")
        ));

        int nextLength = "中文".length() + "🙂".length();
        assertEquals(2, "🙂".length());
        assertHistory(historyService.loadCompletedHistory(user("tenant-a", "owner-a"), "conv-chars", null, 6, latestLength + nextLength), List.of(
            item(2L, ConversationMessageRole.USER, "中文"),
            item(2L, ConversationMessageRole.ASSISTANT, "🙂"),
            item(3L, ConversationMessageRole.USER, "abc"),
            item(3L, ConversationMessageRole.ASSISTANT, "de")
        ));

        assertEquals(List.of(), historyService.loadCompletedHistory(user("tenant-a", "owner-a"), "conv-chars", null, 6, latestLength - 1));
    }

    @Test
    void treatsNullContentAsZeroLengthAndEmptyStringInResult() {
        insertConversation("conv-null", "tenant-a", "owner-a", null);
        insertCompletedTurn("conv-null", 1L, "req-null", null, null);

        assertHistory(historyService.loadCompletedHistory(user("tenant-a", "owner-a"), "conv-null", null, 6, 0), List.of(
            item(1L, ConversationMessageRole.USER, ""),
            item(1L, ConversationMessageRole.ASSISTANT, "")
        ));
    }

    @Test
    void enforcesTenantOwnerAndDeletedConversationIsolation() {
        insertConversation("conv-owned", "tenant-a", "owner-a", null);
        insertConversation("conv-other-tenant", "tenant-b", "owner-b", null);
        insertConversation("conv-other-owner", "tenant-a", "owner-other", null);
        insertConversation("conv-deleted", "tenant-a", "owner-a", Instant.parse("2026-01-01T00:00:00Z"));
        insertCompletedTurn("conv-owned", 1L, "req-owned", "u", "a");
        insertCompletedTurn("conv-other-tenant", 1L, "req-tenant", "u", "a");
        insertCompletedTurn("conv-other-owner", 1L, "req-owner", "u", "a");
        insertCompletedTurn("conv-deleted", 1L, "req-deleted", "u", "a");

        assertEquals(2, historyService.loadCompletedHistory(user("tenant-a", "owner-a"), "conv-owned", null, 6, 100).size());
        assertConversationError(MvpErrorCode.RESOURCE_NOT_FOUND,
            () -> historyService.loadCompletedHistory(user("tenant-a", "owner-a"), "conv-other-tenant", null, 6, 100));
        assertConversationError(MvpErrorCode.RESOURCE_NOT_FOUND,
            () -> historyService.loadCompletedHistory(user("tenant-a", "owner-a"), "conv-other-owner", null, 6, 100));
        assertConversationError(MvpErrorCode.RESOURCE_NOT_FOUND,
            () -> historyService.loadCompletedHistory(user("tenant-a", "owner-a"), "conv-deleted", null, 6, 100));
    }

    @Test
    void ignoresLatestIncompleteTurnsAndReturnsPreviousCompletedHistory() {
        insertConversation("conv-latest-incomplete", "tenant-a", "owner-a", null);
        insertCompletedTurn("conv-latest-incomplete", 1L, "req-1", "u1", "a1");
        insertTurn("conv-latest-incomplete", 2L, "req-2", "COMPLETED", "PENDING", "u2", null);
        insertTurn("conv-latest-incomplete", 3L, "req-3", "COMPLETED", "STREAMING", "u3", null);

        assertHistory(historyService.loadCompletedHistory(user("tenant-a", "owner-a"), "conv-latest-incomplete", null, 6, 100), List.of(
            item(1L, ConversationMessageRole.USER, "u1"),
            item(1L, ConversationMessageRole.ASSISTANT, "a1")
        ));
    }

    private void assertHistory(List<ConversationHistoryItem> actual, List<ConversationHistoryItem> expected) {
        assertEquals(expected.size(), actual.size());
        assertEquals(expected, actual);
    }

    private ConversationHistoryItem item(long turnNo, ConversationMessageRole role, String content) {
        return new ConversationHistoryItem(turnNo, role, content);
    }

    private void assertConversationError(MvpErrorCode expectedCode, ThrowingRunnable runnable) {
        ConversationException exception = assertThrows(ConversationException.class, runnable::run);
        assertEquals(expectedCode, exception.code());
    }

    private CurrentUser user(String tenantId, String userId) {
        return new CurrentUser(tenantId, userId, userId, userId, UserRole.USER);
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
        insertUser(tenantId, userId);
    }

    private void insertUser(String tenantId, String userId) {
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
        conversation.setTitle("Title");
        conversation.setNextTurnNo(1L);
        conversation.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        conversation.setUpdatedAt(conversation.getCreatedAt());
        conversation.setDeletedAt(deletedAt);
        assertEquals(1, conversationMapper.insert(conversation));
    }

    private void insertCompletedTurn(String conversationId, long turnNo, String requestId,
                                     String userContent, String assistantContent) {
        insertTurn(conversationId, turnNo, requestId, "COMPLETED", "COMPLETED", userContent, assistantContent);
    }

    private void insertTurn(String conversationId, long turnNo, String requestId, String userStatus,
                            String assistantStatus, String userContent, String assistantContent) {
        insertMessage("user-" + conversationId + "-" + turnNo, conversationId, turnNo,
            "USER", userStatus, userContent, requestId);
        insertMessage("assistant-" + conversationId + "-" + turnNo, conversationId, turnNo,
            "ASSISTANT", assistantStatus, assistantContent, requestId);
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
        message.setPayloadVersion(1);
        message.setCreatedAt(Instant.parse("2026-02-01T00:00:00Z").plusSeconds(turnNo));
        message.setUpdatedAt(message.getCreatedAt());
        assertEquals(1, conversationMessageMapper.insert(message));
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run();
    }

    @Profile("conversation-test")
    @Configuration
    @Import({ConversationHistoryService.class, ConversationExecutionService.class, ConversationTitleService.class})
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