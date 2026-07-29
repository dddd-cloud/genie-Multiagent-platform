package com.jd.genie.platform.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.DriverManager;
import java.util.Map;

import org.springframework.core.env.MapPropertySource;
import org.springframework.web.context.support.StandardServletEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class SessionRestartPersistenceIntegrationTest {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36").withDatabaseName("genie").withUsername("test").withPassword("test");
    private ServletWebServerApplicationContext first;
    private ServletWebServerApplicationContext second;

    @AfterEach void closeContexts() { if (second != null) second.close(); if (first != null) first.close(); }

    @Test void retainsJdbcSessionAcrossTwoApplicationContexts() throws Exception {
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()).locations("classpath:db/migration").load().migrate();
        try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()); var statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO app_tenant (id,code,name,status,created_at,updated_at) VALUES ('tenant-default','default','Default Tenant','ACTIVE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))");
            String hash = new BCryptPasswordEncoder(12).encode("MvpTest-Only-123").replace("'", "''");
            statement.executeUpdate("INSERT INTO app_user (id,tenant_id,username,display_name,password_hash,role,status,created_at,updated_at,version) VALUES ('restart-user','tenant-default','restartuser','Restart User','" + hash + "','USER','ACTIVE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),0)");
        }
        CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).build();
        first = startContext();
        URI baseOne = URI.create("http://127.0.0.1:" + first.getWebServer().getPort());
        String token = new ObjectMapper().readTree(client.send(HttpRequest.newBuilder(baseOne.resolve("/api/v1/auth/csrf")).GET().build(), HttpResponse.BodyHandlers.ofString()).body()).path("data").path("token").asText();
        HttpResponse<String> login = client.send(HttpRequest.newBuilder(baseOne.resolve("/api/v1/auth/login")).header("Content-Type", "application/json").header("X-XSRF-TOKEN", token).POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"restartuser\",\"password\":\"MvpTest-Only-123\"}")).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, login.statusCode());
        try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()); var statement = connection.createStatement(); var result = statement.executeQuery("SELECT COUNT(*) FROM SPRING_SESSION")) { result.next(); assertTrue(result.getInt(1) > 0); }
        first.close(); first = null;
        second = startContext();
        URI baseTwo = URI.create("http://127.0.0.1:" + second.getWebServer().getPort());
        HttpResponse<String> me = client.send(HttpRequest.newBuilder(baseTwo.resolve("/api/v1/users/me")).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, me.statusCode());
        assertTrue(me.body().contains("restartuser"));
        try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()); var statement = connection.createStatement(); var result = statement.executeQuery("SELECT COUNT(*) FROM SPRING_SESSION_ATTRIBUTES")) { result.next(); assertTrue(result.getInt(1) > 0); }
    }

    private ServletWebServerApplicationContext startContext() {
        StandardServletEnvironment environment = new StandardServletEnvironment();
        environment.setActiveProfiles("test");
        environment.getPropertySources().addFirst(new MapPropertySource("session-restart-testcontainer", Map.of(
            "GENIE_DB_URL", MYSQL.getJdbcUrl(),
            "GENIE_DB_USERNAME", MYSQL.getUsername(),
            "GENIE_DB_PASSWORD", MYSQL.getPassword(),
            "spring.datasource.url", MYSQL.getJdbcUrl(),
            "spring.datasource.username", MYSQL.getUsername(),
            "spring.datasource.password", MYSQL.getPassword(),
            "spring.flyway.url", MYSQL.getJdbcUrl(),
            "spring.flyway.user", MYSQL.getUsername(),
            "spring.flyway.password", MYSQL.getPassword(),
            "server.port", "0"
        )));
        return (ServletWebServerApplicationContext) new SpringApplicationBuilder(Phase3IntegrationTestApplication.class)
            .environment(environment)
            .run();
    }
}
