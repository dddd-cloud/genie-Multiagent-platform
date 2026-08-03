package com.jd.genie.platform.phase2contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.platform.contract.UserRole;
import com.jd.genie.platform.user.dto.CreateUserCommand;
import com.jd.genie.platform.user.entity.UserStatus;
import com.jd.genie.platform.user.mapper.UserMapper;
import com.jd.genie.platform.user.service.TenantService;
import com.jd.genie.platform.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import jakarta.servlet.http.Cookie;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(
    classes = Phase2ContractIntegrationTestApplication.class,
    properties = "GENIE_INTERNAL_AGENT_TOKEN=phase2-c0-token"
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class Phase2SecurityIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
        .withDatabaseName("genie")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantService tenantService;
    @Autowired UserService userService;
    @Autowired UserMapper userMapper;

    @BeforeEach
    void ensureLoginUser() {
        var tenant = tenantService.ensureDefaultTenant();
        if (userMapper.findByTenantIdAndUsername(tenant.getId(), "phase2c0") == null) {
            userService.createUser(new CreateUserCommand(
                tenant.getId(),
                "phase2c0",
                "Phase2 C0",
                "MvpTest-Only-123",
                UserRole.USER,
                UserStatus.ACTIVE
            ));
        }
    }

    @Test
    void v2PathsRequireSessionAndCsrfAndReturn404WhenAuthorized() throws Exception {
        MvcResult csrfResult = mockMvc.perform(get("/api/v1/auth/csrf"))
            .andExpect(status().isOk())
            .andReturn();
        Cookie csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(csrfCookie);
        String token = objectMapper.readTree(csrfResult.getResponse().getContentAsString())
            .get("data").get("token").asText();

        // CSRF is validated before authentication for state-changing requests.
        // With a valid CSRF token but no session, V2 paths must return AUTH_REQUIRED.
        mockMvc.perform(post("/api/v2/agents")
                .cookie(csrfCookie)
                .header("X-XSRF-TOKEN", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
        mockMvc.perform(post("/web/api/v2/gpt/queryAgentStreamIncr")
                .cookie(csrfCookie)
                .header("X-XSRF-TOKEN", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));

        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                .cookie(csrfCookie)
                .header("X-XSRF-TOKEN", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"phase2c0\",\"password\":\"MvpTest-Only-123\"}"))
            .andExpect(status().isOk())
            .andReturn();
        Cookie sessionCookie = login.getResponse().getCookie("GENIE_SESSION");
        assertNotNull(sessionCookie);

        mockMvc.perform(post("/api/v2/agents")
                .cookie(sessionCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        mockMvc.perform(post("/web/api/v2/gpt/queryAgentStreamIncr")
                .cookie(sessionCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("CSRF_INVALID"));

        mockMvc.perform(post("/api/v2/agents")
                .cookie(sessionCookie, csrfCookie)
                .header("X-XSRF-TOKEN", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isNotFound());
        mockMvc.perform(post("/web/api/v2/gpt/queryAgentStreamIncr")
                .cookie(sessionCookie, csrfCookie)
                .header("X-XSRF-TOKEN", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isNotFound());
    }

    @Test
    void autoAgentInternalTokenAndCsrfIgnoreRemainUnchanged() throws Exception {
        mockMvc.perform(post("/AutoAgent").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INTERNAL_TOKEN_INVALID"));

        var entered = mockMvc.perform(post("/AutoAgent")
                .header("X-Genie-Internal-Token", "phase2-c0-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{"))
            .andReturn()
            .getResponse();
        assertNotEquals(401, entered.getStatus());
        assertNotEquals(403, entered.getStatus());

        MockHttpSession browserSession = new MockHttpSession();
        mockMvc.perform(post("/api/v1/auth/login")
                .session(browserSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }
}
