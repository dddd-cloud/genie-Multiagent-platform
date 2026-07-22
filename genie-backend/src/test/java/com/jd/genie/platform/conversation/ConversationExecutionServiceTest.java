package com.jd.genie.platform.conversation;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.jd.genie.platform.contract.ConversationExecutionCommand;
import com.jd.genie.platform.contract.ConversationExecutionResult;
import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.contract.UserRole;
import com.jd.genie.platform.conversation.entity.ConversationEntity;
import com.jd.genie.platform.conversation.entity.ConversationMessageEntity;
import com.jd.genie.platform.conversation.exception.ConversationException;
import com.jd.genie.platform.conversation.mapper.ConversationMapper;
import com.jd.genie.platform.conversation.mapper.ConversationMessageMapper;
import com.jd.genie.platform.conversation.service.ConversationExecutionService;
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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.AdditionalAnswers.delegatesTo;

@Testcontainers
@SpringBootTest(classes = ConversationExecutionServiceTest.TestConfig.class)
class ConversationExecutionServiceTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
        .withDatabaseName("genie_mvp_b_execution")
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

    @Autowired
    private PlatformTransactionManager transactionManager;

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
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS trg_fail_conversation_update");
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
    void prepareExecutionCreatesUserAndAssistantMessagesAndAdvancesConversation() {
        insertConversation("conv-normal", "tenant-a", "owner-a", "新对话", 1L, null);

        ConversationExecutionResult result = executionService.prepareExecution(user("tenant-a", "owner-a"),
            command("conv-normal", "req-normal", "  第一行\n第二行  ", 1, "docs"));

        assertEquals("conv-normal", result.conversationId());
        assertEquals("req-normal", result.requestId());
        assertNotNull(result.userMessageId());
        assertNotNull(result.assistantMessageId());
        assertNotEquals(result.userMessageId(), result.assistantMessageId());
        assertEquals(1L, result.turnNo());

        List<ConversationMessageEntity> messages = conversationMessageMapper.selectMessagesByOwnedConversation(
            "tenant-a", "owner-a", "conv-normal");
        assertEquals(2, messages.size());
        ConversationMessageEntity user = messages.stream().filter(row -> "USER".equals(row.getRole())).findFirst().orElseThrow();
        ConversationMessageEntity assistant = messages.stream().filter(row -> "ASSISTANT".equals(row.getRole())).findFirst().orElseThrow();
        assertEquals(result.userMessageId(), user.getId());
        assertEquals(result.assistantMessageId(), assistant.getId());
        assertEquals(user.getTurnNo(), assistant.getTurnNo());
        assertEquals(user.getRequestId(), assistant.getRequestId());
        assertEquals("COMPLETED", user.getStatus());
        assertEquals("PENDING", assistant.getStatus());
        assertEquals("第一行\n第二行", user.getContent());
        assertNull(assistant.getContent());
        assertEquals(1, user.getDeepThink());
        assertEquals("docs", user.getOutputStyle());
        assertEquals(1, assistant.getDeepThink());
        assertEquals("docs", assistant.getOutputStyle());
        assertEquals(1, user.getPayloadVersion());
        assertEquals(1, assistant.getPayloadVersion());

        ConversationEntity conversation = conversationMapper.selectOwnedConversation("tenant-a", "owner-a", "conv-normal");
        assertEquals(2L, conversation.getNextTurnNo());
        assertNotNull(conversation.getLastMessageAt());
        assertEquals("第一行 第二行", conversation.getTitle());
    }

    @Test
    void prepareExecutionRejectsCrossTenantCrossOwnerAndDeletedConversations() {
        insertConversation("conv-other-tenant", "tenant-b", "owner-b", "Other", 1L, null);
        insertConversation("conv-other-owner", "tenant-a", "owner-other", "Other", 1L, null);
        insertConversation("conv-deleted", "tenant-a", "owner-a", "Deleted", 1L, Instant.parse("2026-01-01T00:00:00Z"));

        assertConversationError(MvpErrorCode.RESOURCE_NOT_FOUND, () -> executionService.prepareExecution(
            user("tenant-a", "owner-a"), command("conv-other-tenant", "req-a", "hello", 0, "docs")));
        assertConversationError(MvpErrorCode.RESOURCE_NOT_FOUND, () -> executionService.prepareExecution(
            user("tenant-a", "owner-a"), command("conv-other-owner", "req-b", "hello", 0, "docs")));
        assertConversationError(MvpErrorCode.RESOURCE_NOT_FOUND, () -> executionService.prepareExecution(
            user("tenant-a", "owner-a"), command("conv-deleted", "req-c", "hello", 0, "docs")));
        assertEquals(0, countMessages("conv-other-tenant"));
        assertEquals(0, countMessages("conv-other-owner"));
        assertEquals(0, countMessages("conv-deleted"));
    }

    @Test
    void duplicateRequestIsDetectedBeforeBusy() {
        insertConversation("conv-dup", "tenant-a", "owner-a", "Title", 2L, null);
        insertUserMessage("msg-user-existing", "conv-dup", 1L, "same", "req-dup");
        insertAssistantMessage("msg-assistant-existing", "conv-dup", 1L, "PENDING", null, "req-dup");

        assertConversationError(MvpErrorCode.DUPLICATE_REQUEST, () -> executionService.prepareExecution(
            user("tenant-a", "owner-a"), command("conv-dup", "req-dup", "hello", 0, "docs")));
        assertEquals(2, countMessages("conv-dup"));
    }

    @Test
    void activeAssistantBlocksDifferentRequestButTerminalAssistantDoesNot() {
        insertConversation("conv-pending", "tenant-a", "owner-a", "Pending", 2L, null);
        insertAssistantMessage("msg-pending", "conv-pending", 1L, "PENDING", null, "req-pending");
        assertConversationError(MvpErrorCode.CONVERSATION_BUSY, () -> executionService.prepareExecution(
            user("tenant-a", "owner-a"), command("conv-pending", "req-new", "hello", 0, "docs")));

        insertConversation("conv-streaming", "tenant-a", "owner-a", "Streaming", 2L, null);
        insertAssistantMessage("msg-streaming", "conv-streaming", 1L, "STREAMING", null, "req-streaming");
        assertConversationError(MvpErrorCode.CONVERSATION_BUSY, () -> executionService.prepareExecution(
            user("tenant-a", "owner-a"), command("conv-streaming", "req-new", "hello", 0, "docs")));

        insertConversation("conv-terminal", "tenant-a", "owner-a", "Terminal", 2L, null);
        insertAssistantMessage("msg-done", "conv-terminal", 1L, "COMPLETED", "done", "req-done");
        ConversationExecutionResult result = executionService.prepareExecution(user("tenant-a", "owner-a"),
            command("conv-terminal", "req-ok", "hello", 0, "docs"));
        assertEquals(2L, result.turnNo());
        assertEquals(3L, conversationMapper.selectOwnedConversation("tenant-a", "owner-a", "conv-terminal").getNextTurnNo());
    }

    @Test
    void rollsBackWhenAssistantInsertFailsAfterUserInsert() {
        insertConversation("conv-assistant-fail", "tenant-a", "owner-a", "Title", 1L, null);
        insertAssistantMessage("msg-existing-assistant", "conv-assistant-fail", 1L, "COMPLETED", "done", "req-existing");

        assertThrows(DuplicateKeyException.class, () -> executionService.prepareExecution(
            user("tenant-a", "owner-a"), command("conv-assistant-fail", "req-new", "hello", 0, "docs")));

        assertEquals(1, countMessages("conv-assistant-fail"));
        ConversationEntity conversation = conversationMapper.selectOwnedConversation(
            "tenant-a", "owner-a", "conv-assistant-fail");
        assertEquals(1L, conversation.getNextTurnNo());
        assertNull(conversation.getLastMessageAt());
    }

    @Test
    void rollsBackWhenConversationUpdateFailsAfterMessageInserts() {
        insertConversation("conv-update-fail", "tenant-a", "owner-a", "Title", 1L, null);
        ConversationMapper failingMapper = mock(ConversationMapper.class, delegatesTo(conversationMapper));
        when(failingMapper.completePrepareExecution(
            anyString(),
            anyString(),
            eq("conv-update-fail"),
            anyLong(),
            any(),
            any(),
            any()
        )).thenThrow(new TransientDataAccessResourceException("test update failure"));
        ConversationExecutionService failingService = new ConversationExecutionService(failingMapper, conversationMessageMapper);
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        assertConversationError(MvpErrorCode.DATABASE_UNAVAILABLE, () -> transactionTemplate.executeWithoutResult(
            status -> failingService.prepareExecution(
                user("tenant-a", "owner-a"),
                command("conv-update-fail", "req-update-fail", "hello", 0, "docs")
            )));

        assertEquals(0, countMessages("conv-update-fail"));
        ConversationEntity conversation = conversationMapper.selectOwnedConversation(
            "tenant-a", "owner-a", "conv-update-fail");
        assertEquals(1L, conversation.getNextTurnNo());
        assertNull(conversation.getLastMessageAt());
    }

    @Test
    void concurrentSameRequestIdAllowsOneSuccessAndOneDuplicateWithoutPartialRows() throws Exception {
        insertConversation("conv-same-req", "tenant-a", "owner-a", "Title", 1L, null);

        List<Object> results = runConcurrently(
            () -> prepareOrCode("conv-same-req", "req-same"),
            () -> prepareOrCode("conv-same-req", "req-same")
        );

        assertEquals(1, results.stream().filter(ConversationExecutionResult.class::isInstance).count());
        assertEquals(1, results.stream().filter(MvpErrorCode.DUPLICATE_REQUEST::equals).count());
        assertEquals(2, countMessages("conv-same-req"));
        assertEquals(2L, conversationMapper.selectOwnedConversation("tenant-a", "owner-a", "conv-same-req").getNextTurnNo());
    }

    @Test
    void concurrentDifferentRequestIdsAllowOneSuccessAndOneBusyWithoutDuplicateTurnOrPartialRows() throws Exception {
        insertConversation("conv-diff-req", "tenant-a", "owner-a", "Title", 1L, null);

        List<Object> results = runConcurrently(
            () -> prepareOrCode("conv-diff-req", "req-one"),
            () -> prepareOrCode("conv-diff-req", "req-two")
        );

        assertEquals(1, results.stream().filter(ConversationExecutionResult.class::isInstance).count());
        assertEquals(1, results.stream().filter(MvpErrorCode.CONVERSATION_BUSY::equals).count());
        List<ConversationMessageEntity> messages = conversationMessageMapper.selectMessagesByOwnedConversation(
            "tenant-a", "owner-a", "conv-diff-req");
        assertEquals(2, messages.size());
        assertEquals(1, messages.stream().map(ConversationMessageEntity::getTurnNo).distinct().count());
        assertTrue(conversationMessageMapper.existsActiveAssistant("tenant-a", "owner-a", "conv-diff-req"));
        assertEquals(2L, conversationMapper.selectOwnedConversation("tenant-a", "owner-a", "conv-diff-req").getNextTurnNo());
    }

    private Object prepareOrCode(String conversationId, String requestId) {
        try {
            return executionService.prepareExecution(user("tenant-a", "owner-a"),
                command(conversationId, requestId, "hello", 0, "docs"));
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

    private void assertConversationError(MvpErrorCode expectedCode, ThrowingRunnable runnable) {
        ConversationException exception = assertThrows(ConversationException.class, runnable::run);
        assertEquals(expectedCode, exception.code());
    }

    private ConversationExecutionCommand command(String conversationId, String requestId, String query,
                                                 Integer deepThink, String outputStyle) {
        return new ConversationExecutionCommand(conversationId, requestId, query, deepThink, outputStyle);
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

    private void insertConversation(String id, String tenantId, String ownerId, String title,
                                    long nextTurnNo, Instant deletedAt) {
        ConversationEntity conversation = new ConversationEntity();
        conversation.setId(id);
        conversation.setTenantId(tenantId);
        conversation.setOwnerId(ownerId);
        conversation.setTitle(title);
        conversation.setNextTurnNo(nextTurnNo);
        conversation.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
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
        message.setCreatedAt(Timestamp.from(Instant.parse("2026-02-01T00:00:00Z").plusSeconds(turnNo)).toInstant());
        message.setUpdatedAt(message.getCreatedAt());
        assertEquals(1, conversationMessageMapper.insert(message));
    }

    private int countMessages(String conversationId) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM conversation_message WHERE conversation_id = ?",
            Integer.class,
            conversationId
        );
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run();
    }

    @SpringBootConfiguration
    @Import(ConversationExecutionService.class)
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
