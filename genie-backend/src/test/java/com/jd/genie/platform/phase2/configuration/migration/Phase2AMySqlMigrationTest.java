package com.jd.genie.platform.phase2.configuration.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class Phase2AMySqlMigrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
        .withDatabaseName("genie_phase2_a")
        .withUsername("genie")
        .withPassword("genie")
        .withStartupTimeout(Duration.ofMinutes(3));

    private static Flyway flyway;

    @BeforeAll
    static void migrate() {
        flyway = Flyway.configure()
            .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
            .locations("classpath:db/migration")
            .validateOnMigrate(true)
            .cleanDisabled(true)
            .baselineOnMigrate(false)
            .load();
        flyway.migrate();
    }

    @Test
    void migratesV001ThroughV004FromEmptyMysqlDatabase() throws Exception {
        Map<String, MigrationState> states = Arrays.stream(flyway.info().all())
            .filter(info -> info.getVersion() != null)
            .collect(Collectors.toMap(info -> info.getVersion().getVersion(), MigrationInfo::getState));

        assertEquals(Set.of("001", "002", "003", "004"), states.keySet());
        assertEquals(MigrationState.SUCCESS, states.get("001"));
        assertEquals(MigrationState.SUCCESS, states.get("002"));
        assertEquals(MigrationState.SUCCESS, states.get("003"));
        assertEquals(MigrationState.SUCCESS, states.get("004"));
        assertEquals(0, flyway.validateWithResult().errorDetails == null ? 0 : 1);
    }

    @Test
    void protectsExistingMigrationContentAndKeepsV004InsidePhase2AScope() throws Exception {
        assertResourceSha256("db/migration/V001__legacy_schema.sql",
            "EF25B071D21D8CE9353A632C9D658C3DA5346593D976EF9177AE11C6612BDCE5");
        assertResourceSha256("db/migration/V002__identity_and_session.sql",
            "6E58DF3539EA57DD6188C32E7B1D9961DC35F89D905502E6B0CCAD3D4D66588E");
        assertResourceSha256("db/migration/V003__conversation.sql",
            "1202CEE1FECFCD047AF378C24054ADFF0A21517824DED6EC1E5462A47D69B920");

        String v004 = readResource("db/migration/V004__agent_and_skill.sql").toLowerCase();
        for (String forbidden : List.of(
            "agent_tool_binding", "skill_tool_binding", "mcp_server", "mcp_tool",
            "run_attempt", "agent_run", "agent_step", "conversation_message", "conversation (",
            "memory", "orchestration")) {
            assertFalse(v004.contains(forbidden), "V004 must not create cross-module object: " + forbidden);
        }
    }

    @Test
    void createsAgentSkillTablesColumnsDefaultsGeneratedColumnsAndConstraints() throws Exception {
        try (Connection connection = MYSQL.createConnection("")) {
            assertTableExists(connection, "agent_definition");
            assertTableExists(connection, "skill_definition");
            assertTableExists(connection, "agent_skill_binding");

            assertColumn(connection, "agent_definition", "id", "varchar", 36, "NO", null);
            assertColumn(connection, "agent_definition", "tenant_id", "varchar", 36, "NO", null);
            assertColumn(connection, "agent_definition", "owner_id", "varchar", 36, "NO", null);
            assertColumn(connection, "agent_definition", "name", "varchar", 128, "NO", null);
            assertColumn(connection, "agent_definition", "description", "varchar", 1000, "NO", null);
            assertColumn(connection, "agent_definition", "prompt_mode", "varchar", 16, "NO", null);
            assertColumn(connection, "agent_definition", "prompt_config", "json", null, "YES", null);
            assertColumn(connection, "agent_definition", "system_prompt", "mediumtext", 16777215, "NO", null);
            assertColumn(connection, "agent_definition", "model_name", "varchar", 128, "YES", null);
            assertColumn(connection, "agent_definition", "status", "varchar", 16, "NO", null);
            assertColumn(connection, "agent_definition", "version", "bigint", null, "NO", "0");
            assertColumn(connection, "agent_definition", "created_at", "datetime", null, "NO", null);
            assertColumn(connection, "agent_definition", "updated_at", "datetime", null, "NO", null);
            assertColumn(connection, "agent_definition", "deleted_at", "datetime", null, "YES", null);
            assertGeneratedColumn(connection, "agent_definition", "active_name", "varchar", 128);
            assertIndex(connection, "agent_definition", "PRIMARY", true, List.of("id"));
            assertIndex(connection, "agent_definition", "uk_agent_active_name", true,
                List.of("tenant_id", "owner_id", "active_name"));

            assertColumn(connection, "skill_definition", "id", "varchar", 36, "NO", null);
            assertColumn(connection, "skill_definition", "tenant_id", "varchar", 36, "NO", null);
            assertColumn(connection, "skill_definition", "owner_id", "varchar", 36, "NO", null);
            assertColumn(connection, "skill_definition", "name", "varchar", 128, "NO", null);
            assertColumn(connection, "skill_definition", "description", "varchar", 1000, "NO", null);
            assertColumn(connection, "skill_definition", "instruction", "mediumtext", 16777215, "NO", null);
            assertColumn(connection, "skill_definition", "output_requirement", "text", 65535, "YES", null);
            assertColumn(connection, "skill_definition", "status", "varchar", 16, "NO", null);
            assertColumn(connection, "skill_definition", "version", "bigint", null, "NO", "0");
            assertColumn(connection, "skill_definition", "created_at", "datetime", null, "NO", null);
            assertColumn(connection, "skill_definition", "updated_at", "datetime", null, "NO", null);
            assertColumn(connection, "skill_definition", "deleted_at", "datetime", null, "YES", null);
            assertGeneratedColumn(connection, "skill_definition", "active_name", "varchar", 128);
            assertIndex(connection, "skill_definition", "PRIMARY", true, List.of("id"));
            assertIndex(connection, "skill_definition", "uk_skill_active_name", true,
                List.of("tenant_id", "owner_id", "active_name"));

            assertColumn(connection, "agent_skill_binding", "tenant_id", "varchar", 36, "NO", null);
            assertColumn(connection, "agent_skill_binding", "owner_id", "varchar", 36, "NO", null);
            assertColumn(connection, "agent_skill_binding", "agent_id", "varchar", 36, "NO", null);
            assertColumn(connection, "agent_skill_binding", "skill_id", "varchar", 36, "NO", null);
            assertColumn(connection, "agent_skill_binding", "sort_order", "int", null, "NO", null);
            assertColumn(connection, "agent_skill_binding", "created_at", "datetime", null, "NO", null);
            assertIndex(connection, "agent_skill_binding", "uk_agent_skill_agent_skill", true,
                List.of("agent_id", "skill_id"));
            assertIndex(connection, "agent_skill_binding", "uk_agent_skill_agent_sort", true,
                List.of("agent_id", "sort_order"));

            assertEquals("InnoDB", tableOption(connection, "agent_definition", "ENGINE"));
            assertTrue(tableOption(connection, "agent_definition", "TABLE_COLLATION").startsWith("utf8mb4"));
            assertEquals("InnoDB", tableOption(connection, "skill_definition", "ENGINE"));
            assertTrue(tableOption(connection, "skill_definition", "TABLE_COLLATION").startsWith("utf8mb4"));
            assertEquals("InnoDB", tableOption(connection, "agent_skill_binding", "ENGINE"));
            assertTrue(tableOption(connection, "agent_skill_binding", "TABLE_COLLATION").startsWith("utf8mb4"));
        }
    }

    @Test
    void enforcesActiveNameIsolationAndSoftDeleteReuseForAgentsAndSkills() throws Exception {
        try (Connection connection = MYSQL.createConnection("")) {
            insertAgent(connection, "agent-a1", "tenant-a", "owner-a", "helper", null);
            assertThrows(SQLException.class, () -> insertAgent(connection, "agent-a2", "tenant-a", "owner-a", "helper", null));
            assertDoesNotThrow(() -> insertAgent(connection, "agent-b1", "tenant-b", "owner-a", "helper", null));
            assertDoesNotThrow(() -> insertAgent(connection, "agent-c1", "tenant-a", "owner-b", "helper", null));
            updateDeletedAt(connection, "agent_definition", "agent-a1");
            assertDoesNotThrow(() -> insertAgent(connection, "agent-a3", "tenant-a", "owner-a", "helper", null));

            insertSkill(connection, "skill-a1", "tenant-a", "owner-a", "search", null);
            assertThrows(SQLException.class, () -> insertSkill(connection, "skill-a2", "tenant-a", "owner-a", "search", null));
            assertDoesNotThrow(() -> insertSkill(connection, "skill-b1", "tenant-b", "owner-a", "search", null));
            assertDoesNotThrow(() -> insertSkill(connection, "skill-c1", "tenant-a", "owner-b", "search", null));
            updateDeletedAt(connection, "skill_definition", "skill-a1");
            assertDoesNotThrow(() -> insertSkill(connection, "skill-a3", "tenant-a", "owner-a", "search", null));
        }
    }

    @Test
    void enforcesBindingUniquenessAndPromptConfigJsonValidity() throws Exception {
        try (Connection connection = MYSQL.createConnection("")) {
            insertAgent(connection, "agent-json", "tenant-json", "owner-json", "json-agent", "{\"sections\":[\"goal\"]}");
            insertAgent(connection, "agent-bind-1", "tenant-bind", "owner-bind", "agent-one", null);
            insertAgent(connection, "agent-bind-2", "tenant-bind", "owner-bind", "agent-two", null);
            insertSkill(connection, "skill-bind-1", "tenant-bind", "owner-bind", "skill-one", null);
            insertSkill(connection, "skill-bind-2", "tenant-bind", "owner-bind", "skill-two", null);
            insertSkill(connection, "skill-bind-3", "tenant-bind", "owner-bind", "skill-three", null);

            insertBinding(connection, "tenant-bind", "owner-bind", "agent-bind-1", "skill-bind-1", 1);
            assertThrows(SQLException.class,
                () -> insertBinding(connection, "tenant-bind", "owner-bind", "agent-bind-1", "skill-bind-1", 2));
            assertThrows(SQLException.class,
                () -> insertBinding(connection, "tenant-bind", "owner-bind", "agent-bind-1", "skill-bind-2", 1));
            assertDoesNotThrow(() -> insertBinding(connection, "tenant-bind", "owner-bind", "agent-bind-2", "skill-bind-3", 1));
        }
    }

    private void assertResourceSha256(String path, String expected) throws Exception {
        String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(readResourceBytes(path)))
            .toUpperCase();
        assertEquals(expected, actual, path);
    }

    private String readResource(String path) throws Exception {
        return new String(readResourceBytes(path), StandardCharsets.UTF_8);
    }

    private byte[] readResourceBytes(String path) throws Exception {
        try (var input = Thread.currentThread().getContextClassLoader().getResourceAsStream(path)) {
            assertNotNull(input, "Missing resource: " + path);
            return input.readAllBytes();
        }
    }

    private void assertTableExists(Connection connection, String table) throws SQLException {
        try (ResultSet resultSet = connection.getMetaData().getTables(null, null, table, new String[]{"TABLE"})) {
            assertTrue(resultSet.next(), "Missing table: " + table);
        }
    }

    private void assertColumn(Connection connection, String table, String column, String dataType, Integer length,
                              String nullable, String defaultValue) throws SQLException {
        Map<String, Object> row = queryOne(connection, """
            SELECT DATA_TYPE, CHARACTER_MAXIMUM_LENGTH, IS_NULLABLE, COLUMN_DEFAULT
            FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?
            """, table, column);
        assertEquals(dataType, stringValue(row.get("DATA_TYPE")), table + "." + column);
        if (length != null) {
            assertEquals(length.longValue(), ((Number) row.get("CHARACTER_MAXIMUM_LENGTH")).longValue(), table + "." + column);
        }
        assertEquals(nullable, stringValue(row.get("IS_NULLABLE")), table + "." + column);
        if (defaultValue == null) {
            assertEquals(null, row.get("COLUMN_DEFAULT"), table + "." + column);
        } else {
            assertEquals(defaultValue, stringValue(row.get("COLUMN_DEFAULT")), table + "." + column);
        }
    }

    private void assertGeneratedColumn(Connection connection, String table, String column, String dataType,
                                       int length) throws SQLException {
        Map<String, Object> row = queryOne(connection, """
            SELECT DATA_TYPE, CHARACTER_MAXIMUM_LENGTH, IS_NULLABLE, EXTRA, GENERATION_EXPRESSION
            FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?
            """, table, column);
        assertEquals(dataType, stringValue(row.get("DATA_TYPE")));
        assertEquals(length, ((Number) row.get("CHARACTER_MAXIMUM_LENGTH")).intValue());
        assertEquals("YES", stringValue(row.get("IS_NULLABLE")));
        assertTrue(stringValue(row.get("EXTRA")).toUpperCase().contains("STORED GENERATED"));
        String expression = stringValue(row.get("GENERATION_EXPRESSION")).toLowerCase();
        assertTrue(expression.contains("deleted_at"));
        assertTrue(expression.contains("name"));
    }

    private void assertIndex(Connection connection, String table, String index, boolean unique,
                             List<String> columns) throws SQLException {
        List<Map<String, Object>> rows = queryList(connection, """
            SELECT COLUMN_NAME, NON_UNIQUE
            FROM information_schema.statistics
            WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ?
            ORDER BY SEQ_IN_INDEX
            """, table, index);
        assertEquals(columns.size(), rows.size(), table + "." + index);
        assertEquals(columns, rows.stream().map(row -> stringValue(row.get("COLUMN_NAME"))).toList());
        assertTrue(rows.stream().allMatch(row -> (((Number) row.get("NON_UNIQUE")).intValue() == 0) == unique));
    }

    private String tableOption(Connection connection, String table, String option) throws SQLException {
        return stringValue(queryOne(connection,
            "SELECT " + option + " FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?",
            table).get(option));
    }

    private void insertAgent(Connection connection, String id, String tenantId, String ownerId, String name,
                             String promptConfig) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO agent_definition(
                id, tenant_id, owner_id, name, description, prompt_mode, prompt_config, system_prompt,
                model_name, status, created_at, updated_at
            ) VALUES (?, ?, ?, ?, 'description', 'RAW', ?, 'system prompt', NULL, 'DRAFT', NOW(6), NOW(6))
            """)) {
            statement.setString(1, id);
            statement.setString(2, tenantId);
            statement.setString(3, ownerId);
            statement.setString(4, name);
            statement.setString(5, promptConfig);
            statement.executeUpdate();
        }
    }

    private void insertSkill(Connection connection, String id, String tenantId, String ownerId, String name,
                             String outputRequirement) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO skill_definition(
                id, tenant_id, owner_id, name, description, instruction, output_requirement, status, created_at, updated_at
            ) VALUES (?, ?, ?, ?, 'description', 'instruction', ?, 'ENABLED', NOW(6), NOW(6))
            """)) {
            statement.setString(1, id);
            statement.setString(2, tenantId);
            statement.setString(3, ownerId);
            statement.setString(4, name);
            statement.setString(5, outputRequirement);
            statement.executeUpdate();
        }
    }

    private void insertBinding(Connection connection, String tenantId, String ownerId, String agentId,
                               String skillId, int sortOrder) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO agent_skill_binding(tenant_id, owner_id, agent_id, skill_id, sort_order, created_at)
            VALUES (?, ?, ?, ?, ?, NOW(6))
            """)) {
            statement.setString(1, tenantId);
            statement.setString(2, ownerId);
            statement.setString(3, agentId);
            statement.setString(4, skillId);
            statement.setInt(5, sortOrder);
            statement.executeUpdate();
        }
    }

    private void updateDeletedAt(Connection connection, String table, String id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("UPDATE " + table + " SET deleted_at = NOW(6) WHERE id = ?")) {
            statement.setString(1, id);
            statement.executeUpdate();
        }
    }

    private Map<String, Object> queryOne(Connection connection, String sql, Object... args) throws SQLException {
        List<Map<String, Object>> rows = queryList(connection, sql, args);
        assertEquals(1, rows.size(), sql);
        return rows.get(0);
    }

    private List<Map<String, Object>> queryList(Connection connection, String sql, Object... args) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                statement.setObject(i + 1, args[i]);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                new org.springframework.jdbc.core.ColumnMapRowMapper();
                List<Map<String, Object>> rows = new java.util.ArrayList<>();
                var mapper = new org.springframework.jdbc.core.ColumnMapRowMapper();
                int rowNum = 0;
                while (resultSet.next()) {
                    rows.add(mapper.mapRow(resultSet, rowNum++));
                }
                return rows;
            }
        }
    }

    private String stringValue(Object value) {
        if (value instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return value == null ? null : String.valueOf(value);
    }
}