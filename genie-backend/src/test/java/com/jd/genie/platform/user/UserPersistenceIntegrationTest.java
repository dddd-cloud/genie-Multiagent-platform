package com.jd.genie.platform.user;

import com.jd.genie.platform.contract.UserRole;
import com.jd.genie.platform.user.config.BootstrapProperties;
import com.jd.genie.platform.user.dto.CreateUserCommand;
import com.jd.genie.platform.user.entity.TenantEntity;
import com.jd.genie.platform.user.entity.UserEntity;
import com.jd.genie.platform.user.entity.UserStatus;
import com.jd.genie.platform.user.mapper.TenantMapper;
import com.jd.genie.platform.user.mapper.UserMapper;
import com.jd.genie.platform.user.service.BootstrapAdminService;
import com.jd.genie.platform.user.service.TenantService;
import com.jd.genie.platform.user.service.UserAlreadyExistsException;
import com.jd.genie.platform.user.service.UserService;
import com.jd.genie.platform.user.service.UserValidationException;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class UserPersistenceIntegrationTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-22T00:00:00Z"), ZoneOffset.UTC);

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
        .withDatabaseName("genie")
        .withUsername("test")
        .withPassword("test");

    private static SqlSession sqlSession;
    private static TenantMapper tenantMapper;
    private static UserMapper userMapper;
    private static PasswordEncoder passwordEncoder;
    private static TenantService tenantService;
    private static UserService userService;

    @BeforeAll
    static void setUpDatabase() {
        Flyway.configure()
            .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
            .locations("classpath:db/migration")
            .validateOnMigrate(true)
            .cleanDisabled(true)
            .baselineOnMigrate(false)
            .load()
            .migrate();

        PooledDataSource dataSource = new PooledDataSource(
            "com.mysql.cj.jdbc.Driver", MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()
        );
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
        configuration.setEnvironment(new Environment("test", new JdbcTransactionFactory(), dataSource));
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(TenantMapper.class);
        configuration.addMapper(UserMapper.class);
        SqlSessionFactory sessionFactory = new SqlSessionFactoryBuilder().build(configuration);
        sqlSession = sessionFactory.openSession(true);
        tenantMapper = sqlSession.getMapper(TenantMapper.class);
        userMapper = sqlSession.getMapper(UserMapper.class);
        passwordEncoder = new BCryptPasswordEncoder(12);
        tenantService = new TenantService(tenantMapper, CLOCK);
        userService = new UserService(userMapper, passwordEncoder, CLOCK);
    }

    @AfterAll
    static void closeSession() {
        if (sqlSession != null) {
            sqlSession.close();
        }
    }

    @BeforeEach
    void clearIdentityTables() throws Exception {
        try (Statement statement = sqlSession.getConnection().createStatement()) {
            statement.executeUpdate("DELETE FROM app_user");
            statement.executeUpdate("DELETE FROM app_tenant");
        }
        sqlSession.clearCache();
    }

    @Test
    void persistsNormalizedUserAndBcryptHash() {
        TenantEntity tenant = tenantService.ensureDefaultTenant();
        UserEntity created = userService.createUser(new CreateUserCommand(
            tenant.getId(), "  User.Name  ", "  用户一  ", "MvpTest-Only-123",
            UserRole.USER, UserStatus.ACTIVE
        ));

        UserEntity stored = userMapper.findByTenantIdAndUsername(tenant.getId(), "user.name");
        assertNotNull(stored);
        assertEquals("user.name", stored.getUsername());
        assertEquals("用户一", stored.getDisplayName());
        assertEquals(UserRole.USER, stored.getRole());
        assertEquals(UserStatus.ACTIVE, stored.getStatus());
        assertEquals(0, stored.getVersion());
        assertTrue(stored.getPasswordHash().startsWith("$2a$12$") || stored.getPasswordHash().startsWith("$2b$12$"));
        assertTrue(passwordEncoder.matches("MvpTest-Only-123", stored.getPasswordHash()));
        assertEquals(CLOCK.instant(), created.getCreatedAt().toInstant(ZoneOffset.UTC));
    }

    @Test
    void enforcesUsernameValidationAndDatabaseUniqueness() {
        TenantEntity tenant = tenantService.ensureDefaultTenant();
        assertThrows(UserValidationException.class, () -> userService.createUser(new CreateUserCommand(
            tenant.getId(), "ab", "User", "MvpTest-Only-123", UserRole.USER, UserStatus.ACTIVE
        )));

        UserEntity first = userService.createUser(new CreateUserCommand(
            tenant.getId(), "User-A", "User A", "MvpTest-Only-123", UserRole.USER, UserStatus.ACTIVE
        ));
        assertThrows(UserAlreadyExistsException.class, () -> userService.createUser(new CreateUserCommand(
            tenant.getId(), "user-a", "User A", "MvpTest-Only-123", UserRole.USER, UserStatus.ACTIVE
        )));

        UserEntity duplicate = new UserEntity();
        duplicate.setId("duplicate-user-id");
        duplicate.setTenantId(first.getTenantId());
        duplicate.setUsername(first.getUsername());
        duplicate.setDisplayName(first.getDisplayName());
        duplicate.setPasswordHash(first.getPasswordHash());
        duplicate.setRole(first.getRole());
        duplicate.setStatus(first.getStatus());
        duplicate.setCreatedAt(first.getCreatedAt());
        duplicate.setUpdatedAt(first.getUpdatedAt());
        duplicate.setVersion(0);
        assertThrows(PersistenceException.class, () -> userMapper.insert(duplicate));
    }

    @Test
    void bootstrapsDefaultTenantAndAdminIdempotently() {
        BootstrapProperties properties = new BootstrapProperties();
        properties.setAdminUsername(" Admin ");
        properties.setAdminPassword("MvpAdmin-Test-123");
        BootstrapAdminService bootstrapService = new BootstrapAdminService(
            tenantService, userService, userMapper, properties
        );

        bootstrapService.initialize(false);
        bootstrapService.initialize(false);

        TenantEntity tenant = tenantMapper.findByCode(TenantService.DEFAULT_TENANT_CODE);
        assertNotNull(tenant);
        UserEntity admin = userMapper.findByTenantIdAndUsername(tenant.getId(), "admin");
        assertNotNull(admin);
        assertEquals(1, userMapper.countAll());
        assertEquals(UserRole.ADMIN, admin.getRole());
        assertEquals(UserStatus.ACTIVE, admin.getStatus());
        assertTrue(passwordEncoder.matches("MvpAdmin-Test-123", admin.getPasswordHash()));
    }
}
