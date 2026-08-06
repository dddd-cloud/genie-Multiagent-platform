package com.jd.genie.platform.phase2.tooling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class Phase2BMySqlMigrationTest {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36").withDatabaseName("genie").withUsername("test").withPassword("test");
    @Test void v005CreatesAllBTables() throws Exception {
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()).locations("classpath:db/migration").validateOnMigrate(true).load().migrate();
        try (var c=DriverManager.getConnection(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword()); var s=c.createStatement(); var rs=s.executeQuery("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='genie' AND table_name IN ('mcp_server','mcp_tool','agent_tool_binding','skill_tool_binding')")){rs.next();assertEquals(4,rs.getInt(1));}
    }
}
