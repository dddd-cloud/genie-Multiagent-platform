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
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Testcontainers
@ActiveProfiles("conversation-test")
@SpringBootTest(classes = ConversationTitleServiceTest.TestConfig.class)
class ConversationTitleServiceTest {
    private static final String DEFAULT_TITLE = ConversationTitleService.DEFAULT_TITLE;

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
        .withDatabaseName("genie_mvp_b_title")
        .withUsername("genie")
        .withPassword("genie");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ConversationExecutionService executionService;

    @Autowired
    private ConversationTitleService titleService;

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
    }

    @Test
    void titleAlgorithmNormalizesWhitespaceAndUsesDefaultForBlank() {
        assertEquals("hello world", titleService.generateTitle("  hello\n\r\tworld  "));
        assertEquals(DEFAULT_TITLE, titleService.generateTitle(null));
        assertEquals(DEFAULT_TITLE, titleService.generateTitle(" \n\t  "));
    }

    @Test
    void titleAlgorithmKeepsChineseEmojiAndTruncatesByUnicodeCodePoint() {
        String thirtyCodePoints = "一二三四五六七八九十" + "一二三四五六七八九十" + "一二三四五六七八九十";
        assertEquals(30, titleService.generateTitle(thirtyCodePoints).codePointCount(0, thirtyCodePoints.length()));
        String withExtra = thirtyCodePoints + "🙂tail";
        assertEquals(thirtyCodePoints, titleService.generateTitle(withExtra));
        String emojiTitle = "🙂".repeat(30) + "尾巴";
        String generated = titleService.generateTitle(emojiTitle);
        assertEquals(30, generated.codePointCount(0, generated.length()));
        assertEquals("🙂".repeat(30), generated);
    }

    @Test
    void firstTurnDefaultTitleIsAutoUpdatedInsidePrepareTransaction() {
        insertConversation("conv-first", DEFAULT_TITLE, 1L);

        ConversationExecutionResult result = executionService.prepareExecution(user(),
            command("conv-first", "req-first", "  第一行\n第二行  "));

        assertEquals(1L, result.turnNo());
        ConversationEntity conversation = conversationMapper.selectOwnedConversation("tenant-a", "owner-a", "conv-first");
        assertEquals("第一行 第二行", conversation.getTitle());
        assertEquals(2L, conversation.getNextTurnNo());
        assertEquals(2L, conversation.getVersion());
    }

    @Test
    void secondTurnDoesNotAutoTitleAgain() {
        insertConversation("conv-second", DEFAULT_TITLE, 2L);

        ConversationExecutionResult result = executionService.prepareExecution(user(),
            command("conv-second", "req-second", "second turn title"));

        assertEquals(2L, result.turnNo());
        assertEquals(DEFAULT_TITLE, conversationMapper.selectOwnedConversation("tenant-a", "owner-a", "conv-second").getTitle());
    }

    @Test
    void manualTitleIsNotOverwrittenByAutoTitleOrConditionalUpdateRace() {
        insertConversation("conv-manual", "Manual", 1L);

        executionService.prepareExecution(user(), command("conv-manual", "req-manual", "auto title"));
        assertEquals("Manual", conversationMapper.selectOwnedConversation("tenant-a", "owner-a", "conv-manual").getTitle());

        int updated = conversationMapper.autoTitleFirstTurnIfDefault(
            "tenant-a", "owner-a", "conv-manual", 2L, DEFAULT_TITLE, "forced", Instant.now());
        assertEquals(0, updated);
        assertEquals("Manual", conversationMapper.selectOwnedConversation("tenant-a", "owner-a", "conv-manual").getTitle());
    }

    @Test
    void duplicateAndBusyRequestsDoNotModifyDefaultTitle() {
        insertConversation("conv-dup", DEFAULT_TITLE, 1L);
        insertUserMessage("msg-user-existing", "conv-dup", 1L, "old", "req-dup");
        insertAssistantMessage("msg-assistant-existing", "conv-dup", 1L, "PENDING", "req-dup");
        assertConversationError(MvpErrorCode.DUPLICATE_REQUEST,
            () -> executionService.prepareExecution(user(), command("conv-dup", "req-dup", "new title")));
        assertEquals(DEFAULT_TITLE, conversationMapper.selectOwnedConversation("tenant-a", "owner-a", "conv-dup").getTitle());

        insertConversation("conv-busy", DEFAULT_TITLE, 1L);
        insertAssistantMessage("msg-busy", "conv-busy", 1L, "STREAMING", "req-busy");
        assertConversationError(MvpErrorCode.CONVERSATION_BUSY,
            () -> executionService.prepareExecution(user(), command("conv-busy", "req-new", "busy title")));
        assertEquals(DEFAULT_TITLE, conversationMapper.selectOwnedConversation("tenant-a", "owner-a", "conv-busy").getTitle());
    }

    @Test
    void titleUpdateFailureRollsBackPrepareExecution() {
        insertConversation("conv-title-fail", DEFAULT_TITLE, 1L);
        ConversationMapper failingMapper = mock(ConversationMapper.class, delegatesTo(conversationMapper));
        when(failingMapper.autoTitleFirstTurnIfDefault(
            anyString(), anyString(), eq("conv-title-fail"), anyLong(), anyString(), anyString(), any()
        )).thenThrow(new TransientDataAccessResourceException("title update failure"));
        ConversationExecutionService failingService = new ConversationExecutionService(
            failingMapper,
            conversationMessageMapper,
            new ConversationHistoryService(failingMapper, conversationMessageMapper),
            new ConversationTitleService(failingMapper)
        );
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        ConversationException exception = assertThrows(ConversationException.class, () -> transactionTemplate.executeWithoutResult(
            status -> failingService.prepareExecution(user(), command("conv-title-fail", "req-fail", "rollback title"))));
        assertEquals(MvpErrorCode.DATABASE_UNAVAILABLE, exception.code());

        ConversationEntity conversation = conversationMapper.selectOwnedConversation("tenant-a", "owner-a", "conv-title-fail");
        assertEquals(DEFAULT_TITLE, conversation.getTitle());
        assertEquals(1L, conversation.getNextTurnNo());
        assertEquals(0, countMessages("conv-title-fail"));
    }

    private void assertConversationError(MvpErrorCode expectedCode, ThrowingRunnable runnable) {
        ConversationException exception = assertThrows(ConversationException.class, runnable::run);
        assertEquals(expectedCode, exception.code());
    }

    private ConversationExecutionCommand command(String conversationId, String requestId, String query) {
        return new ConversationExecutionCommand(conversationId, requestId, query, 0, "docs");
    }

    private CurrentUser user() {
        return new CurrentUser("tenant-a", "owner-a", "owner-a", "owner-a", UserRole.USER);
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

    private void insertConversation(String id, String title, long nextTurnNo) {
        ConversationEntity conversation = new ConversationEntity();
        conversation.setId(id);
        conversation.setTenantId("tenant-a");
        conversation.setOwnerId("owner-a");
        conversation.setTitle(title);
        conversation.setNextTurnNo(nextTurnNo);
        conversation.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        conversation.setUpdatedAt(conversation.getCreatedAt());
        assertEquals(1, conversationMapper.insert(conversation));
    }

    private void insertUserMessage(String id, String conversationId, long turnNo, String content, String requestId) {
        insertMessage(id, conversationId, turnNo, "USER", "COMPLETED", content, requestId);
    }

    private void insertAssistantMessage(String id, String conversationId, long turnNo, String status, String requestId) {
        insertMessage(id, conversationId, turnNo, "ASSISTANT", status, null, requestId);
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

    private int countMessages(String conversationId) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM conversation_message WHERE conversation_id = ?", Integer.class, conversationId);
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run();
    }

    @Profile("conversation-test")
    @Configuration
    @Import({ConversationExecutionService.class, ConversationHistoryService.class, ConversationTitleService.class})
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