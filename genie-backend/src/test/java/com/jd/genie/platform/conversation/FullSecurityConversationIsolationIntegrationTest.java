package com.jd.genie.platform.conversation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.platform.contract.UserRole;
import com.jd.genie.platform.conversation.config.ConversationMapperConfiguration;
import com.jd.genie.platform.conversation.controller.ConversationController;
import com.jd.genie.platform.conversation.exception.ConversationExceptionHandler;
import com.jd.genie.platform.conversation.service.ConversationService;
import com.jd.genie.platform.security.CurrentUserDetailsService;
import com.jd.genie.platform.security.SecurityConfig;
import com.jd.genie.platform.security.SecurityProperties;
import com.jd.genie.platform.security.SessionCurrentUserProvider;
import com.jd.genie.platform.user.config.UserMapperConfiguration;
import com.jd.genie.platform.user.controller.AuthUtilityController;
import com.jd.genie.platform.user.dto.CreateUserCommand;
import com.jd.genie.platform.user.entity.UserStatus;
import com.jd.genie.platform.user.mapper.UserMapper;
import com.jd.genie.platform.user.service.TenantService;
import com.jd.genie.platform.user.service.UserService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(classes = FullSecurityConversationIsolationIntegrationTest.TestApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class FullSecurityConversationIsolationIntegrationTest {
    private static final String PASSWORD = "MvpTest-Only-123";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
        .withDatabaseName("genie_mvp_b_security")
        .withUsername("genie")
        .withPassword("genie");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TenantService tenantService;

    @Autowired
    private UserService userService;

    @Autowired
    private UserMapper userMapper;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
    }

    @BeforeEach
    void users() {
        var tenant = tenantService.ensureDefaultTenant();
        ensureUser(tenant.getId(), "user-a");
        ensureUser(tenant.getId(), "user-b");
    }

    @Test
    void realSecurityChainKeepsUserBFromReadingOrMutatingUserAConversation() throws Exception {
        Login userA = login("user-a");
        Login userB = login("user-b");

        MvcResult created = mockMvc.perform(post("/api/v1/conversations")
                .cookie(userA.session(), userA.csrf())
                .header("X-XSRF-TOKEN", userA.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Owned by user A\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OK"))
            .andReturn();
        String conversationId = objectMapper.readTree(created.getResponse().getContentAsString())
            .path("data")
            .path("id")
            .asText();
        assertNotNull(conversationId);

        mockMvc.perform(get("/api/v1/conversations/{conversationId}", conversationId)
                .cookie(userB.session()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(patch("/api/v1/conversations/{conversationId}", conversationId)
                .cookie(userB.session(), userB.csrf())
                .header("X-XSRF-TOKEN", userB.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"user B rename\"}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(delete("/api/v1/conversations/{conversationId}", conversationId)
                .cookie(userB.session(), userB.csrf())
                .header("X-XSRF-TOKEN", userB.token()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/conversations/{conversationId}/messages", conversationId)
                .cookie(userB.session()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    private void ensureUser(String tenantId, String username) {
        if (userMapper.findByTenantIdAndUsername(tenantId, username) == null) {
            userService.createUser(new CreateUserCommand(
                tenantId,
                username,
                username,
                PASSWORD,
                UserRole.USER,
                UserStatus.ACTIVE
            ));
        }
    }

    private Login login(String username) throws Exception {
        MvcResult csrf = mockMvc.perform(get("/api/v1/auth/csrf"))
            .andExpect(status().isOk())
            .andReturn();
        Cookie csrfCookie = csrf.getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(csrfCookie);
        String token = objectMapper.readTree(csrf.getResponse().getContentAsString())
            .path("data")
            .path("token")
            .asText();
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                .cookie(csrfCookie)
                .header("X-XSRF-TOKEN", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"))
            .andExpect(status().isOk())
            .andReturn();
        Cookie session = login.getResponse().getCookie("GENIE_SESSION");
        assertNotNull(session);
        return new Login(session, csrfCookie, token);
    }

    private record Login(Cookie session, Cookie csrf, String token) {
    }

    @Configuration
    @EnableAutoConfiguration
    @Import({
        AuthUtilityController.class,
        ConversationController.class,
        ConversationExceptionHandler.class,
        ConversationMapperConfiguration.class,
        ConversationService.class,
        CurrentUserDetailsService.class,
        SecurityConfig.class,
        SecurityProperties.class,
        SessionCurrentUserProvider.class,
        TenantService.class,
        UserMapperConfiguration.class,
        UserService.class
    })
    static class TestApplication {
        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }

        @Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder(12);
        }
    }
}