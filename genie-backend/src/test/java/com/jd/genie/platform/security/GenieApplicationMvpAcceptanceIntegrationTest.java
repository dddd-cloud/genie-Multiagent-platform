package com.jd.genie.platform.security;

import com.jd.genie.GenieApplication;
import com.jd.genie.config.DataAgentInitRunner;
import com.jd.genie.platform.agentbridge.acceptance.FakeAgentAcceptanceFilter;
import com.jd.genie.platform.contract.CurrentUserProvider;
import com.jd.genie.platform.contract.UserRole;
import com.jd.genie.platform.user.entity.UserEntity;
import com.jd.genie.platform.user.entity.UserStatus;
import com.jd.genie.platform.user.mapper.UserMapper;
import com.jd.genie.platform.user.service.AcceptanceUserSeeder;
import com.jd.genie.platform.user.service.TenantService;
import com.jd.genie.platform.user.service.UserService;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.filter.TypeExcludeFilters;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@SpringBootTest(classes = GenieApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TypeExcludeFilters(ConversationTestConfigurationExcludeFilter.class)
@ActiveProfiles("mvp-acceptance")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class GenieApplicationMvpAcceptanceIntegrationTest {
    private static final String ADMIN_PASSWORD = "acceptance-admin-password";
    private static final String USER_PASSWORD = "acceptance-user-password";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
        .withDatabaseName("genie")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("GENIE_DB_URL", MYSQL::getJdbcUrl);
        registry.add("GENIE_DB_USERNAME", MYSQL::getUsername);
        registry.add("GENIE_DB_PASSWORD", MYSQL::getPassword);
        registry.add("GENIE_INTERNAL_AGENT_TOKEN", () -> "acceptance-internal-token");
        registry.add("MVP_ACCEPTANCE_ADMIN_PASSWORD", () -> ADMIN_PASSWORD);
        registry.add("MVP_ACCEPTANCE_USER_PASSWORD", () -> USER_PASSWORD);
    }

    @Autowired
    ApplicationContext applicationContext;
    @Autowired
    TenantService tenantService;
    @Autowired
    UserService userService;
    @Autowired
    UserMapper userMapper;
    @Autowired
    AcceptanceUserSeeder acceptanceUserSeeder;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    Flyway flyway;

    @Test
    void startsFullApplicationAndSeedsStableAcceptanceUsers() {
        assertNotNull(applicationContext.getBean(SecurityFilterChain.class));
        Map<String, CurrentUserProvider> currentUserProviders = applicationContext.getBeansOfType(CurrentUserProvider.class);
        assertFalse(currentUserProviders.isEmpty());
        assertTrue(currentUserProviders.containsKey("sessionCurrentUserProvider"));
        assertFalse(currentUserProviders.containsKey("currentUserProvider"),
            () -> beanDefinitionDescription("currentUserProvider"));
        assertNotNull(applicationContext.getBean(SessionCurrentUserProvider.class));
        assertNotNull(applicationContext.getBean(UserService.class));
        assertNotNull(applicationContext.getBean(TenantService.class));
        assertNotNull(flyway);
        assertFalse(applicationContext.containsBean("dataAgentInitRunner"));
        assertFalse(applicationContext.containsBean("bootstrapAdminRunner"));
        assertNotNull(applicationContext.getBean(FakeAgentAcceptanceFilter.class));

        assertEquals(TenantService.DEFAULT_TENANT_ID, tenantService.ensureDefaultTenant().getId());
        Map<String, UserEntity> users = Map.of(
            "admin", requiredUser("admin"),
            "user-a", requiredUser("user-a"),
            "user-b", requiredUser("user-b")
        );
        assertUser(users.get("admin"), UserRole.ADMIN, ADMIN_PASSWORD);
        assertUser(users.get("user-a"), UserRole.USER, USER_PASSWORD);
        assertUser(users.get("user-b"), UserRole.USER, USER_PASSWORD);

        long countBefore = userMapper.countAll();
        Map<String, String> idsBefore = Map.of(
            "admin", users.get("admin").getId(),
            "user-a", users.get("user-a").getId(),
            "user-b", users.get("user-b").getId()
        );
        acceptanceUserSeeder.seed();
        assertEquals(countBefore, userMapper.countAll());
        for (Map.Entry<String, String> entry : idsBefore.entrySet()) {
            assertEquals(entry.getValue(), requiredUser(entry.getKey()).getId());
        }
    }

    private UserEntity requiredUser(String username) {
        UserEntity user = userMapper.findByTenantIdAndUsername(TenantService.DEFAULT_TENANT_ID, username);
        assertNotNull(user);
        return user;
    }

    private String beanDefinitionDescription(String beanName) {
        BeanDefinition definition = ((ConfigurableApplicationContext) applicationContext)
            .getBeanFactory()
            .getBeanDefinition(beanName);
        return "Unexpected bean '" + beanName + "': beanClassName=" + definition.getBeanClassName()
            + ", factoryBeanName=" + definition.getFactoryBeanName()
            + ", factoryMethodName=" + definition.getFactoryMethodName()
            + ", resourceDescription=" + definition.getResourceDescription();
    }

    private void assertUser(UserEntity user, UserRole role, String password) {
        assertEquals(TenantService.DEFAULT_TENANT_ID, user.getTenantId());
        assertEquals(role, user.getRole());
        assertEquals(UserStatus.ACTIVE, user.getStatus());
        assertNotEquals(password, user.getPasswordHash());
        assertFalse(user.getPasswordHash().isBlank());
        assertTrue(passwordEncoder.matches(password, user.getPasswordHash()));
    }
}
