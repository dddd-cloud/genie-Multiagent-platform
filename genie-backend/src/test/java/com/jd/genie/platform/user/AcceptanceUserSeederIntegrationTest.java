package com.jd.genie.platform.user;

import com.jd.genie.platform.contract.UserRole;
import com.jd.genie.platform.user.dto.CreateUserCommand;
import com.jd.genie.platform.user.entity.TenantEntity;
import com.jd.genie.platform.user.entity.TenantStatus;
import com.jd.genie.platform.user.entity.UserEntity;
import com.jd.genie.platform.user.entity.UserStatus;
import com.jd.genie.platform.user.mapper.TenantMapper;
import com.jd.genie.platform.user.mapper.UserMapper;
import com.jd.genie.platform.user.service.AcceptanceUserSeeder;
import com.jd.genie.platform.user.service.TenantService;
import com.jd.genie.platform.user.service.UserService;
import java.time.Clock;
import java.time.LocalDateTime;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
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
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testcontainers
class AcceptanceUserSeederIntegrationTest {
    private static final String ADMIN_PASSWORD = "acceptance-admin-password";
    private static final String USER_PASSWORD = "acceptance-user-password";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
        .withDatabaseName("genie")
        .withUsername("test")
        .withPassword("test");

    private static SqlSession sqlSession;
    private static TenantMapper tenantMapper;
    private static UserMapper userMapper;
    private static TenantService tenantService;
    private static UserService userService;

    @BeforeAll
    static void setUp() {
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
            .locations("classpath:db/migration").load().migrate();
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
        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(12);
        tenantService = new TenantService(tenantMapper, Clock.systemUTC());
        userService = new UserService(userMapper, passwordEncoder, Clock.systemUTC());
    }

    @AfterAll
    static void closeSession() {
        if (sqlSession != null) {
            sqlSession.close();
        }
    }

    @BeforeEach
    void clearIdentityTables() throws Exception {
        try (var statement = sqlSession.getConnection().createStatement()) {
            statement.executeUpdate("DELETE FROM app_user");
            statement.executeUpdate("DELETE FROM app_tenant");
        }
        sqlSession.clearCache();
    }

    @Test
    void rejectsMissingAcceptancePasswordsWithoutRevealingValues() {
        IllegalStateException missingAdmin = assertThrows(IllegalStateException.class,
            () -> seeder(new MockEnvironment()).seed());
        assertEquals("MVP_ACCEPTANCE_ADMIN_PASSWORD is required", missingAdmin.getMessage());

        MockEnvironment missingUser = new MockEnvironment()
            .withProperty("MVP_ACCEPTANCE_ADMIN_PASSWORD", ADMIN_PASSWORD);
        IllegalStateException missingUserError = assertThrows(IllegalStateException.class,
            () -> seeder(missingUser).seed());
        assertEquals("MVP_ACCEPTANCE_USER_PASSWORD is required", missingUserError.getMessage());

        IllegalStateException blankAdmin = assertThrows(IllegalStateException.class,
            () -> seeder(new MockEnvironment().withProperty("MVP_ACCEPTANCE_ADMIN_PASSWORD", " ")).seed());
        assertEquals("MVP_ACCEPTANCE_ADMIN_PASSWORD is required", blankAdmin.getMessage());

        IllegalStateException blankUser = assertThrows(IllegalStateException.class,
            () -> seeder(new MockEnvironment()
                .withProperty("MVP_ACCEPTANCE_ADMIN_PASSWORD", ADMIN_PASSWORD)
                .withProperty("MVP_ACCEPTANCE_USER_PASSWORD", " ")).seed());
        assertEquals("MVP_ACCEPTANCE_USER_PASSWORD is required", blankUser.getMessage());
    }

    @Test
    void rejectsWrongAdminRoleWithoutOverwritingIt() {
        TenantEntity tenant = tenantService.ensureDefaultTenant();
        UserEntity admin = userService.createUser(new CreateUserCommand(
            tenant.getId(), "admin", "Existing Admin", ADMIN_PASSWORD, UserRole.USER, UserStatus.ACTIVE
        ));

        assertThrows(IllegalStateException.class, () -> seeder(configuredEnvironment()).seed());
        UserEntity stored = userMapper.findByTenantIdAndUsername(tenant.getId(), "admin");
        assertEquals(admin.getId(), stored.getId());
        assertEquals(UserRole.USER, stored.getRole());
    }

    @Test
    void rejectsDisabledAcceptanceUserWithoutReenablingIt() {
        TenantEntity tenant = tenantService.ensureDefaultTenant();
        userService.createUser(new CreateUserCommand(
            tenant.getId(), "admin", "Existing Admin", ADMIN_PASSWORD, UserRole.ADMIN, UserStatus.ACTIVE
        ));
        UserEntity userA = userService.createUser(new CreateUserCommand(
            tenant.getId(), "user-a", "Existing User", USER_PASSWORD, UserRole.USER, UserStatus.DISABLED
        ));

        assertThrows(IllegalStateException.class, () -> seeder(configuredEnvironment()).seed());
        UserEntity stored = userMapper.findByTenantIdAndUsername(tenant.getId(), "user-a");
        assertEquals(userA.getId(), stored.getId());
        assertEquals(UserStatus.DISABLED, stored.getStatus());
    }

    @Test
    void rejectsAcceptanceUsernameFromAnotherTenant() {
        TenantEntity defaultTenant = tenantService.ensureDefaultTenant();
        TenantEntity otherTenant = new TenantEntity();
        otherTenant.setId("tenant-other");
        otherTenant.setCode("other");
        otherTenant.setName("Other Tenant");
        otherTenant.setStatus(TenantStatus.ACTIVE);
        otherTenant.setCreatedAt(LocalDateTime.now());
        otherTenant.setUpdatedAt(LocalDateTime.now());
        tenantMapper.insert(otherTenant);
        UserEntity wrongTenantAdmin = userService.createUser(new CreateUserCommand(
            otherTenant.getId(), "admin", "Other Admin", ADMIN_PASSWORD, UserRole.ADMIN, UserStatus.ACTIVE
        ));

        assertThrows(IllegalStateException.class, () -> seeder(configuredEnvironment()).seed());
        assertEquals(wrongTenantAdmin.getId(), userMapper.findByTenantIdAndUsername(otherTenant.getId(), "admin").getId());
        assertNull(userMapper.findByTenantIdAndUsername(defaultTenant.getId(), "admin"));
    }

    private AcceptanceUserSeeder seeder(MockEnvironment environment) {
        return new AcceptanceUserSeeder(tenantService, userService, userMapper, environment);
    }

    private MockEnvironment configuredEnvironment() {
        return new MockEnvironment()
            .withProperty("MVP_ACCEPTANCE_ADMIN_PASSWORD", ADMIN_PASSWORD)
            .withProperty("MVP_ACCEPTANCE_USER_PASSWORD", USER_PASSWORD);
    }
}
