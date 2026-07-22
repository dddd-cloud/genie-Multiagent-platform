package com.jd.genie.platform.conversation;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.MessageCompletionCommand;
import com.jd.genie.platform.contract.MessageFailureCommand;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.contract.UserRole;
import com.jd.genie.platform.conversation.entity.ConversationEntity;
import com.jd.genie.platform.conversation.entity.ConversationMessageEntity;
import com.jd.genie.platform.conversation.exception.ConversationException;
import com.jd.genie.platform.conversation.mapper.ConversationMapper;
import com.jd.genie.platform.conversation.mapper.ConversationMessageMapper;
import com.jd.genie.platform.conversation.service.ConversationExecutionService;
import com.jd.genie.platform.conversation.service.ConversationHistoryService;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@SpringBootTest(classes = ConversationMessageStateMachineTest.TestConfig.class)
class ConversationMessageStateMachineTest {
    private static final String VALID_SNAPSHOT = "{\"payloadVersion\":1,\"events\":[]}";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
        .withDatabaseName("genie_mvp_b_state_machine")
        .withUsername("genie")
        .withPassword("genie");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ConversationExecutionService executionService;

    @Autowired
    private ConversationMapper conversationMapper;

    @Autowired
    private ConversationMessageMapper conversationMessageMapper;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
        registry.add("GENIE_STREAM_SNAPSHOT_MAX_BYTES", () -> "64");
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
    void markStreamingAllowsOnlyOwnedPendingAssistant() {
        insertConversation("conv-mark", "tenant-a", "owner-a", null);
        insertMessage("msg-pending", "conv-mark", 1L, "ASSISTANT", "PENDING", null, "req-mark");

        executionService.markStreaming(user("tenant-a", "owner-a"), "msg-pending");

        MessageRow row = message("msg-pending");
        assertEquals("STREAMING", row.status());
        assertEquals(1L, row.version());

        assertConversationError(MvpErrorCode.MESSAGE_STATE_CONFLICT,
            () -> executionService.markStreaming(user("tenant-a", "owner-a"), "msg-pending"));

        insertMessage("msg-completed", "conv-mark", 2L, "ASSISTANT", "COMPLETED", "done", "req-completed");
        insertMessage("msg-failed", "conv-mark", 3L, "ASSISTANT", "FAILED", null, "req-failed");
        insertMessage("msg-interrupted", "conv-mark", 4L, "ASSISTANT", "INTERRUPTED", null, "req-interrupted");
        insertMessage("msg-user", "conv-mark", 5L, "USER", "PENDING", null, "req-user");
        assertStateConflict(() -> executionService.markStreaming(user("tenant-a", "owner-a"), "msg-completed"));
        assertStateConflict(() -> executionService.markStreaming(user("tenant-a", "owner-a"), "msg-failed"));
        assertStateConflict(() -> executionService.markStreaming(user("tenant-a", "owner-a"), "msg-interrupted"));
        assertStateConflict(() -> executionService.markStreaming(user("tenant-a", "owner-a"), "msg-user"));
    }

    @Test
    void completeAllowsOnlyOwnedStreamingAssistantAndIsAtomicForInvalidSnapshot() {
        insertConversation("conv-complete", "tenant-a", "owner-a", null);
        insertMessage("msg-streaming", "conv-complete", 1L, "ASSISTANT", "STREAMING", null, "req-complete");
        jdbcTemplate.update("UPDATE conversation_message SET error_code='OLD', error_message='old' WHERE id='msg-streaming'");

        executionService.complete(user("tenant-a", "owner-a"),
            new MessageCompletionCommand("msg-streaming", "final answer", VALID_SNAPSHOT, 1));

        MessageRow completed = message("msg-streaming");
        assertEquals("COMPLETED", completed.status());
        assertEquals("final answer", completed.content());
        assertEquals(VALID_SNAPSHOT, completed.streamSnapshot());
        assertEquals(1, completed.payloadVersion());
        assertNull(completed.errorCode());
        assertNull(completed.errorMessage());

        insertMessage("msg-pending-complete", "conv-complete", 2L, "ASSISTANT", "PENDING", null, "req-pending");
        assertStateConflict(() -> executionService.complete(user("tenant-a", "owner-a"),
            new MessageCompletionCommand("msg-pending-complete", "final", VALID_SNAPSHOT, 1)));

        insertMessage("msg-invalid-snapshot", "conv-complete", 3L, "ASSISTANT", "STREAMING", null, "req-invalid");
        assertConversationError(MvpErrorCode.SNAPSHOT_INVALID, () -> executionService.complete(user("tenant-a", "owner-a"),
            new MessageCompletionCommand("msg-invalid-snapshot", "final", "[]", 1)));
        MessageRow invalid = message("msg-invalid-snapshot");
        assertEquals("STREAMING", invalid.status());
        assertNull(invalid.content());
        assertNull(invalid.streamSnapshot());

        insertMessage("msg-large-snapshot", "conv-complete", 4L, "ASSISTANT", "STREAMING", null, "req-large");
        assertConversationError(MvpErrorCode.SNAPSHOT_TOO_LARGE, () -> executionService.complete(user("tenant-a", "owner-a"),
            new MessageCompletionCommand("msg-large-snapshot", "final", largeSnapshot(), 1)));
        MessageRow large = message("msg-large-snapshot");
        assertEquals("STREAMING", large.status());
        assertNull(large.content());
        assertNull(large.streamSnapshot());
    }

    @Test
    void concurrentCompleteAllowsOnlyOneTerminalWrite() throws Exception {
        insertConversation("conv-concurrent", "tenant-a", "owner-a", null);
        insertMessage("msg-concurrent", "conv-concurrent", 1L, "ASSISTANT", "STREAMING", null, "req-concurrent");

        List<Object> results = runConcurrently(
            () -> completeOrCode("msg-concurrent", "final-a"),
            () -> completeOrCode("msg-concurrent", "final-b")
        );

        assertEquals(1, results.stream().filter("OK"::equals).count());
        assertEquals(1, results.stream().filter(MvpErrorCode.MESSAGE_STATE_CONFLICT::equals).count());
        MessageRow row = message("msg-concurrent");
        assertEquals("COMPLETED", row.status());
        assertNotNull(row.content());
        assertEquals(1L, row.version());
    }

    @Test
    void failTransitionsActiveAssistantAndDiscardsInvalidPartialSnapshot() {
        insertConversation("conv-fail", "tenant-a", "owner-a", null);
        insertMessage("msg-fail-pending", "conv-fail", 1L, "ASSISTANT", "PENDING", null, "req-fail-pending");
        insertMessage("msg-fail-streaming", "conv-fail", 2L, "ASSISTANT", "STREAMING", null, "req-fail-streaming");
        insertMessage("msg-fail-invalid", "conv-fail", 3L, "ASSISTANT", "STREAMING", null, "req-fail-invalid");

        executionService.fail(user("tenant-a", "owner-a"),
            new MessageFailureCommand("msg-fail-pending", "AGENT_STREAM_INTERRUPTED", "failed", VALID_SNAPSHOT, 1));
        executionService.fail(user("tenant-a", "owner-a"),
            new MessageFailureCommand("msg-fail-streaming", "AGENT_STREAM_INTERRUPTED", "failed", null, null));
        executionService.fail(user("tenant-a", "owner-a"),
            new MessageFailureCommand("msg-fail-invalid", "SNAPSHOT_INVALID", "failed", "[]", 1));

        MessageRow pending = message("msg-fail-pending");
        assertEquals("FAILED", pending.status());
        assertEquals(VALID_SNAPSHOT, pending.streamSnapshot());
        assertEquals("AGENT_STREAM_INTERRUPTED", pending.errorCode());

        MessageRow streaming = message("msg-fail-streaming");
        assertEquals("FAILED", streaming.status());
        assertNull(streaming.streamSnapshot());
        assertEquals(1, streaming.payloadVersion());

        MessageRow invalid = message("msg-fail-invalid");
        assertEquals("FAILED", invalid.status());
        assertNull(invalid.streamSnapshot());

        assertStateConflict(() -> executionService.fail(user("tenant-a", "owner-a"),
            new MessageFailureCommand("msg-fail-pending", "AGENT_STREAM_INTERRUPTED", "again", null, 1)));
    }

    @Test
    void interruptTransitionsActiveAssistantAndDiscardsInvalidPartialSnapshot() {
        insertConversation("conv-interrupt", "tenant-a", "owner-a", null);
        insertMessage("msg-interrupt-pending", "conv-interrupt", 1L, "ASSISTANT", "PENDING", null, "req-interrupt-pending");
        insertMessage("msg-interrupt-streaming", "conv-interrupt", 2L, "ASSISTANT", "STREAMING", null, "req-interrupt-streaming");
        insertMessage("msg-interrupt-invalid", "conv-interrupt", 3L, "ASSISTANT", "STREAMING", null, "req-interrupt-invalid");

        executionService.interrupt(user("tenant-a", "owner-a"),
            new MessageFailureCommand("msg-interrupt-pending", "CLIENT_DISCONNECTED", "interrupted", VALID_SNAPSHOT, 1));
        executionService.interrupt(user("tenant-a", "owner-a"),
            new MessageFailureCommand("msg-interrupt-streaming", "CLIENT_DISCONNECTED", "interrupted", null, null));
        executionService.interrupt(user("tenant-a", "owner-a"),
            new MessageFailureCommand("msg-interrupt-invalid", "SNAPSHOT_INVALID", "interrupted", largeSnapshot(), 1));

        assertEquals("INTERRUPTED", message("msg-interrupt-pending").status());
        assertEquals(VALID_SNAPSHOT, message("msg-interrupt-pending").streamSnapshot());
        assertEquals("INTERRUPTED", message("msg-interrupt-streaming").status());
        assertNull(message("msg-interrupt-streaming").streamSnapshot());
        assertEquals("INTERRUPTED", message("msg-interrupt-invalid").status());
        assertNull(message("msg-interrupt-invalid").streamSnapshot());
    }

    @Test
    void stateUpdatesDoNotRevealOrModifyCrossTenantCrossOwnerOrDeletedConversations() {
        insertConversation("conv-other-tenant", "tenant-b", "owner-b", null);
        insertConversation("conv-other-owner", "tenant-a", "owner-other", null);
        insertConversation("conv-deleted", "tenant-a", "owner-a", Instant.parse("2026-01-01T00:00:00Z"));
        insertMessage("msg-other-tenant", "conv-other-tenant", 1L, "ASSISTANT", "STREAMING", null, "req-tenant");
        insertMessage("msg-other-owner", "conv-other-owner", 1L, "ASSISTANT", "STREAMING", null, "req-owner");
        insertMessage("msg-deleted", "conv-deleted", 1L, "ASSISTANT", "STREAMING", null, "req-deleted");

        for (String id : List.of("msg-other-tenant", "msg-other-owner", "msg-deleted")) {
            assertStateConflict(() -> executionService.markStreaming(user("tenant-a", "owner-a"), id));
            assertStateConflict(() -> executionService.complete(user("tenant-a", "owner-a"),
                new MessageCompletionCommand(id, "final", VALID_SNAPSHOT, 1)));
            assertStateConflict(() -> executionService.fail(user("tenant-a", "owner-a"),
                new MessageFailureCommand(id, "CLIENT_DISCONNECTED", "failed", null, 1)));
            assertStateConflict(() -> executionService.interrupt(user("tenant-a", "owner-a"),
                new MessageFailureCommand(id, "CLIENT_DISCONNECTED", "interrupted", null, 1)));
            assertEquals("STREAMING", message(id).status());
            assertNull(message(id).content());
        }
    }

    private Object completeOrCode(String assistantMessageId, String content) {
        try {
            executionService.complete(user("tenant-a", "owner-a"),
                new MessageCompletionCommand(assistantMessageId, content, VALID_SNAPSHOT, 1));
            return "OK";
        } catch (ConversationException exception) {
            return exception.code();
        }
    }

    private List<Object> runConcurrently(Callable<Object> first, Callable<Object> second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Callable<Object>> tasks = List.of(
                () -> {
                    start.await();
                    return first.call();
                },
                () -> {
                    start.await();
                    return second.call();
                }
            );
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<Object> task : tasks) {
                futures.add(executor.submit(task));
            }
            start.countDown();
            List<Object> results = new ArrayList<>();
            for (Future<Object> future : futures) {
                results.add(future.get());
            }
            results.sort(Comparator.comparing(Object::toString));
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    private String largeSnapshot() {
        String snapshot = "{\"payloadVersion\":1,\"text\":\"" + "a".repeat(80) + "\"}";
        assertTrue(snapshot.getBytes(StandardCharsets.UTF_8).length > 64);
        return snapshot;
    }

    private void assertStateConflict(ThrowingRunnable runnable) {
        assertConversationError(MvpErrorCode.MESSAGE_STATE_CONFLICT, runnable);
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

    private MessageRow message(String id) {
        return jdbcTemplate.queryForObject("""
            SELECT id, status, content, stream_snapshot, payload_version, error_code, error_message, version
            FROM conversation_message
            WHERE id = ?
            """, (ResultSet rs, int rowNum) -> new MessageRow(
            rs.getString("id"),
            rs.getString("status"),
            rs.getString("content"),
            rs.getString("stream_snapshot"),
            rs.getInt("payload_version"),
            rs.getString("error_code"),
            rs.getString("error_message"),
            rs.getLong("version")
        ), id);
    }

    private record MessageRow(
        String id,
        String status,
        String content,
        String streamSnapshot,
        int payloadVersion,
        String errorCode,
        String errorMessage,
        long version
    ) {
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run();
    }

    @SpringBootConfiguration
    @Import({ConversationExecutionService.class, ConversationHistoryService.class})
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
