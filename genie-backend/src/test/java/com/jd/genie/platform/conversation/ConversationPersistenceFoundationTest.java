package com.jd.genie.platform.conversation;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.conversation.entity.ConversationEntity;
import com.jd.genie.platform.conversation.entity.ConversationMessageEntity;
import com.jd.genie.platform.conversation.exception.DuplicateConstraintClassifier;
import com.jd.genie.platform.conversation.mapper.ConversationMapper;
import com.jd.genie.platform.conversation.mapper.ConversationMessageMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DataAccessException;
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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@SpringBootTest(classes = ConversationPersistenceFoundationTest.TestConfig.class)
class ConversationPersistenceFoundationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
        .withDatabaseName("genie_mvp_b")
        .withUsername("genie")
        .withPassword("genie");

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
    void v003CreatesContractTablesColumnsDefaultsIndexesAndForeignKeys() {
        assertTableExists("conversation");
        assertTableExists("conversation_message");

        assertColumn("conversation", "id", "varchar", 36, "NO", null);
        assertColumn("conversation", "tenant_id", "varchar", 36, "NO", null);
        assertColumn("conversation", "owner_id", "varchar", 36, "NO", null);
        assertColumn("conversation", "title", "varchar", 200, "NO", null);
        assertColumn("conversation", "next_turn_no", "bigint", null, "NO", "1");
        assertColumn("conversation", "last_message_at", "datetime", null, "YES", null);
        assertColumn("conversation", "created_at", "datetime", null, "NO", null);
        assertColumn("conversation", "updated_at", "datetime", null, "NO", null);
        assertColumn("conversation", "deleted_at", "datetime", null, "YES", null);
        assertColumn("conversation", "version", "bigint", null, "NO", "0");

        assertColumn("conversation_message", "id", "varchar", 36, "NO", null);
        assertColumn("conversation_message", "conversation_id", "varchar", 36, "NO", null);
        assertColumn("conversation_message", "turn_no", "bigint", null, "NO", null);
        assertColumn("conversation_message", "role", "varchar", 16, "NO", null);
        assertColumn("conversation_message", "status", "varchar", 16, "NO", null);
        assertColumn("conversation_message", "request_id", "varchar", 64, "NO", null);
        assertColumn("conversation_message", "content", "mediumtext", 16777215, "YES", null);
        assertColumn("conversation_message", "stream_snapshot", "longtext", 4294967295L, "YES", null);
        assertColumn("conversation_message", "payload_version", "int", null, "NO", "1");
        assertColumn("conversation_message", "deep_think", "tinyint", null, "YES", null);
        assertColumn("conversation_message", "output_style", "varchar", 32, "YES", null);
        assertColumn("conversation_message", "error_code", "varchar", 64, "YES", null);
        assertColumn("conversation_message", "error_message", "varchar", 1000, "YES", null);
        assertColumn("conversation_message", "created_at", "datetime", null, "NO", null);
        assertColumn("conversation_message", "updated_at", "datetime", null, "NO", null);
        assertColumn("conversation_message", "version", "bigint", null, "NO", "0");

        assertIndex("conversation", "PRIMARY", true, List.of("id"));
        assertIndex("conversation", "idx_conv_owner_last", false,
            List.of("tenant_id", "owner_id", "last_message_at", "created_at", "id"));
        assertIndex("conversation", "idx_conv_owner_deleted", false,
            List.of("tenant_id", "owner_id", "deleted_at"));
        assertIndex("conversation_message", "PRIMARY", true, List.of("id"));
        assertIndex("conversation_message", "uk_msg_turn_role", true,
            List.of("conversation_id", "turn_no", "role"));
        assertIndex("conversation_message", "uk_msg_request_role", true,
            List.of("conversation_id", "request_id", "role"));
        assertIndex("conversation_message", "idx_msg_conv_turn", false,
            List.of("conversation_id", "turn_no"));
        assertIndex("conversation_message", "idx_msg_conv_status", false,
            List.of("conversation_id", "status"));

        assertForeignKey("conversation", "fk_conv_tenant", "tenant_id", "app_tenant", "id", "NO ACTION", "NO ACTION");
        assertForeignKey("conversation", "fk_conv_owner", "owner_id", "app_user", "id", "NO ACTION", "NO ACTION");
        assertForeignKey("conversation_message", "fk_msg_conversation", "conversation_id", "conversation", "id",
            "NO ACTION", "NO ACTION");
        assertEquals("InnoDB", tableOption("conversation", "ENGINE"));
        assertEquals("utf8mb4", tableOption("conversation", "TABLE_COLLATION").split("_")[0]);
        assertEquals("InnoDB", tableOption("conversation_message", "ENGINE"));
        assertEquals("utf8mb4", tableOption("conversation_message", "TABLE_COLLATION").split("_")[0]);
    }

    @Test
    void mapperScansAndOwnedConversationQueriesFilterTenantOwnerAndDeletedRows() {
        assertNotNull(conversationMapper);
        assertNotNull(conversationMessageMapper);
        insertConversation("conv-owned", "tenant-a", "owner-a", null);
        insertConversation("conv-other-tenant", "tenant-b", "owner-b", null);
        insertConversation("conv-other-owner", "tenant-a", "owner-other", null);
        insertConversation("conv-deleted", "tenant-a", "owner-a", Instant.parse("2026-01-01T00:00:00Z"));

        ConversationEntity owned = conversationMapper.selectOwnedConversation("tenant-a", "owner-a", "conv-owned");

        assertNotNull(owned);
        assertEquals("conv-owned", owned.getId());
        assertEquals(1L, owned.getNextTurnNo());
        assertEquals(0L, owned.getVersion());
        assertFalse(conversationMessageMapper.existsRequestId("tenant-a", "owner-a", "conv-owned", "req-1"));
        assertFalse(conversationMessageMapper.existsActiveAssistant("tenant-a", "owner-a", "conv-owned"));
        assertEquals(null, conversationMapper.selectOwnedConversation("tenant-a", "owner-a", "conv-other-tenant"));
        assertEquals(null, conversationMapper.selectOwnedConversation("tenant-a", "owner-a", "conv-other-owner"));
        assertEquals(null, conversationMapper.selectOwnedConversation("tenant-a", "owner-a", "conv-deleted"));
    }

    @Test
    void messageInsertDefaultsAndUniqueConstraintsUseRealMysql() {
        insertConversation("conv-msg", "tenant-a", "owner-a", null);
        ConversationMessageEntity message = new ConversationMessageEntity();
        message.setId("msg-user-1");
        message.setConversationId("conv-msg");
        message.setTurnNo(1L);
        message.setRole("USER");
        message.setStatus("COMPLETED");
        message.setRequestId("req-1");
        message.setContent("hello");
        message.setDeepThink(1);
        message.setOutputStyle("docs");
        message.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        message.setUpdatedAt(Instant.parse("2026-01-01T00:00:00Z"));

        assertEquals(1, conversationMessageMapper.insert(message));
        Map<String, Object> stored = jdbcTemplate.queryForMap(
            "SELECT payload_version, content, deep_think, output_style FROM conversation_message WHERE id='msg-user-1'");
        assertEquals(1, ((Number) stored.get("payload_version")).intValue());
        assertEquals("hello", stored.get("content"));
        assertEquals(1, ((Number) stored.get("deep_think")).intValue());
        assertEquals("docs", stored.get("output_style"));
        assertTrue(conversationMessageMapper.existsRequestId("tenant-a", "owner-a", "conv-msg", "req-1"));
        assertEquals(1, conversationMessageMapper.selectMessagesByOwnedConversation(
            "tenant-a", "owner-a", "conv-msg").size());

        ConversationMessageEntity duplicateTurnRole = copyMessage(message, "msg-user-dup-turn", "req-2");
        Exception turnRoleException = assertThrows(Exception.class,
            () -> conversationMessageMapper.insert(duplicateTurnRole));
        assertEquals(MvpErrorCode.MESSAGE_STATE_CONFLICT,
            DuplicateConstraintClassifier.classify(turnRoleException).orElseThrow());

        ConversationMessageEntity assistant = copyMessage(message, "msg-asst-1", "req-1");
        assistant.setRole("ASSISTANT");
        assistant.setStatus("PENDING");
        assistant.setTurnNo(2L);
        assertEquals(1, conversationMessageMapper.insert(assistant));
        assertTrue(conversationMessageMapper.existsActiveAssistant("tenant-a", "owner-a", "conv-msg"));

        ConversationMessageEntity duplicateRequestRole = copyMessage(assistant, "msg-asst-dup-request", "req-1");
        duplicateRequestRole.setTurnNo(3L);
        Exception requestRoleException = assertThrows(Exception.class,
            () -> conversationMessageMapper.insert(duplicateRequestRole));
        assertEquals(MvpErrorCode.DUPLICATE_REQUEST,
            DuplicateConstraintClassifier.classify(requestRoleException).orElseThrow());
    }


    @Test
    void duplicateConstraintClassifierMapsUnknownMysqlDuplicateToInternalError() {
        jdbcTemplate.execute("""
            CREATE TABLE duplicate_classifier_unknown (
                id BIGINT NOT NULL AUTO_INCREMENT,
                code VARCHAR(16) NOT NULL,
                PRIMARY KEY (id),
                UNIQUE KEY uk_classifier_unknown (code)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);
        jdbcTemplate.update("INSERT INTO duplicate_classifier_unknown(code) VALUES (?)", "same");

        DataAccessException exception = assertThrows(DataAccessException.class,
            () -> jdbcTemplate.update("INSERT INTO duplicate_classifier_unknown(code) VALUES (?)", "same"));

        assertEquals(MvpErrorCode.INTERNAL_ERROR, DuplicateConstraintClassifier.classify(exception).orElseThrow());
    }
    @Test
    void selectOwnedConversationForUpdateExecutesOnMysql() {
        insertConversation("conv-lock", "tenant-a", "owner-a", null);
        TransactionTemplate tx = new TransactionTemplate(transactionManager());

        ConversationEntity locked = tx.execute(status ->
            conversationMapper.selectOwnedConversationForUpdate("tenant-a", "owner-a", "conv-lock"));

        assertNotNull(locked);
        assertEquals("conv-lock", locked.getId());
    }

    @Autowired
    private PlatformTransactionManager platformTransactionManager;

    private PlatformTransactionManager transactionManager() {
        return platformTransactionManager;
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
        conversation.setTitle("New chat");
        conversation.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        conversation.setUpdatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        conversation.setDeletedAt(deletedAt);
        assertEquals(1, conversationMapper.insert(conversation));
    }

    private ConversationMessageEntity copyMessage(ConversationMessageEntity source, String id, String requestId) {
        ConversationMessageEntity copy = new ConversationMessageEntity();
        copy.setId(id);
        copy.setConversationId(source.getConversationId());
        copy.setTurnNo(source.getTurnNo());
        copy.setRole(source.getRole());
        copy.setStatus(source.getStatus());
        copy.setRequestId(requestId);
        copy.setContent(source.getContent());
        copy.setStreamSnapshot(source.getStreamSnapshot());
        copy.setPayloadVersion(source.getPayloadVersion());
        copy.setDeepThink(source.getDeepThink());
        copy.setOutputStyle(source.getOutputStyle());
        copy.setErrorCode(source.getErrorCode());
        copy.setErrorMessage(source.getErrorMessage());
        copy.setCreatedAt(source.getCreatedAt());
        copy.setUpdatedAt(source.getUpdatedAt());
        return copy;
    }

    private void assertTableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM information_schema.tables
            WHERE table_schema = DATABASE() AND table_name = ?
            """, Integer.class, tableName);
        assertEquals(1, count);
    }

    private void assertColumn(String tableName, String columnName, String dataType, Number length,
                              String nullable, String defaultValue) {
        Map<String, Object> column = jdbcTemplate.queryForMap("""
            SELECT DATA_TYPE, CHARACTER_MAXIMUM_LENGTH, IS_NULLABLE, COLUMN_DEFAULT
            FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?
            """, tableName, columnName);
        assertEquals(dataType, stringValue(column.get("DATA_TYPE")));
        if (length != null) {
            assertEquals(length.longValue(), ((Number) column.get("CHARACTER_MAXIMUM_LENGTH")).longValue());
        }
        assertEquals(nullable, stringValue(column.get("IS_NULLABLE")));
        if (defaultValue == null) {
            assertEquals(null, column.get("COLUMN_DEFAULT"));
        } else {
            assertEquals(defaultValue, stringValue(column.get("COLUMN_DEFAULT")));
        }
    }

    private void assertIndex(String tableName, String indexName, boolean unique, List<String> columns) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT COLUMN_NAME, NON_UNIQUE
            FROM information_schema.statistics
            WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ?
            ORDER BY SEQ_IN_INDEX
            """, tableName, indexName);
        assertEquals(columns.size(), rows.size(), tableName + "." + indexName);
        assertEquals(columns, rows.stream().map(row -> stringValue(row.get("COLUMN_NAME"))).toList());
        assertTrue(rows.stream().allMatch(row -> (((Number) row.get("NON_UNIQUE")).intValue() == 0) == unique));
    }

    private void assertForeignKey(String tableName, String constraintName, String columnName,
                                  String referencedTable, String referencedColumn,
                                  String deleteRule, String updateRule) {
        Map<String, Object> fk = jdbcTemplate.queryForMap("""
            SELECT k.COLUMN_NAME, k.REFERENCED_TABLE_NAME, k.REFERENCED_COLUMN_NAME,
                   r.DELETE_RULE, r.UPDATE_RULE
            FROM information_schema.key_column_usage k
            JOIN information_schema.referential_constraints r
              ON r.CONSTRAINT_SCHEMA = k.CONSTRAINT_SCHEMA
             AND r.CONSTRAINT_NAME = k.CONSTRAINT_NAME
            WHERE k.table_schema = DATABASE()
              AND k.table_name = ?
              AND k.constraint_name = ?
            """, tableName, constraintName);
        assertEquals(columnName, stringValue(fk.get("COLUMN_NAME")));
        assertEquals(referencedTable, stringValue(fk.get("REFERENCED_TABLE_NAME")));
        assertEquals(referencedColumn, stringValue(fk.get("REFERENCED_COLUMN_NAME")));
        assertEquals(deleteRule, stringValue(fk.get("DELETE_RULE")));
        assertEquals(updateRule, stringValue(fk.get("UPDATE_RULE")));
    }

    private String tableOption(String tableName, String column) {
        return jdbcTemplate.queryForObject(
            "SELECT " + column + " FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?",
            String.class, tableName);
    }

    private String stringValue(Object value) {
        if (value instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return value == null ? null : String.valueOf(value);
    }

    @Configuration
    @ImportAutoConfiguration({
        DataSourceAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class,
        JdbcTemplateAutoConfiguration.class,
        MybatisPlusAutoConfiguration.class
    })
    @MapperScan("com.jd.genie.platform.conversation.mapper")
    static class TestConfig {
        @Bean
        String mybatisPlusMapperScanBasePackageMarker() {
            return "com.jd.genie.platform.conversation.mapper";
        }
    }
}
