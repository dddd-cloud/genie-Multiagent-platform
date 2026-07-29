package com.jd.genie.platform.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class MySqlFlywayMigrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
        .withDatabaseName("genie")
        .withUsername("test")
        .withPassword("test");

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
    void migratesLegacyIdentityAndSpringSessionTables() throws SQLException {
        Set<String> expectedTables = Set.of(
            "chat_model_info", "chat_model_schema", "sales_data", "app_tenant", "app_user",
            "SPRING_SESSION", "SPRING_SESSION_ATTRIBUTES"
        );

        try (Connection connection = MYSQL.createConnection("")) {
            for (String table : expectedTables) {
                assertTrue(tableExists(connection, table), "Missing table: " + table);
            }
        }
    }

    @Test
    void validatesAppliedMigrationsWithoutSchemaInitializationFallback() {
        assertEquals(0, flyway.validateWithResult().errorDetails == null ? 0 : 1);
        Map<String, MigrationState> migrationStates = java.util.Arrays.stream(flyway.info().all())
            .filter(migration -> migration.getVersion() != null)
            .collect(Collectors.toMap(
                migration -> migration.getVersion().getVersion(),
                migration -> migration.getState()
            ));

        assertEquals(MigrationState.SUCCESS, migrationStates.get("001"));
        assertEquals(MigrationState.SUCCESS, migrationStates.get("002"));
        if (migrationStates.containsKey("003")) {
            assertEquals(MigrationState.SUCCESS, migrationStates.get("003"));
        }
    }

    private static boolean tableExists(Connection connection, String table) throws SQLException {
        try (ResultSet resultSet = connection.getMetaData().getTables(null, null, table, new String[]{"TABLE"})) {
            return resultSet.next();
        }
    }
}
