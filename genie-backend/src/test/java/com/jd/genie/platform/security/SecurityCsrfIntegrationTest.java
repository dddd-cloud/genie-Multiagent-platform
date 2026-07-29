package com.jd.genie.platform.security;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(classes = Phase3IntegrationTestApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SecurityCsrfIntegrationTest {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36").withDatabaseName("genie").withUsername("test").withPassword("test");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantService tenantService;
    @Autowired UserService userService;
    @Autowired UserMapper userMapper;

    @BeforeEach void ensureLoginUser() {
        var tenant = tenantService.ensureDefaultTenant();
        if (userMapper.findByTenantIdAndUsername(tenant.getId(), "csrfuser") == null) {
            userService.createUser(new CreateUserCommand(tenant.getId(), "csrfuser", "CSRF User", "MvpTest-Only-123", UserRole.USER, UserStatus.ACTIVE));
        }
    }

    @Test void csrfGuardsLoginAndLogoutWithoutLeakingSecrets() throws Exception {
        MvcResult csrfResult = mockMvc.perform(get("/api/v1/auth/csrf"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("OK")).andReturn();
        var csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(csrfCookie);
        JsonNode csrf = objectMapper.readTree(csrfResult.getResponse().getContentAsString()).get("data");
        String token = csrf.get("token").asText();

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"csrfuser\",\"password\":\"MvpTest-Only-123\"}"))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        mockMvc.perform(post("/api/v1/auth/login").cookie(csrfCookie).header("X-XSRF-TOKEN", "wrong").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"csrfuser\",\"password\":\"MvpTest-Only-123\"}"))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));

        MvcResult login = mockMvc.perform(post("/api/v1/auth/login").cookie(csrfCookie).header("X-XSRF-TOKEN", token).contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"csrfuser\",\"password\":\"MvpTest-Only-123\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("OK")).andReturn();
        var sessionCookie = login.getResponse().getCookie("GENIE_SESSION");
        assertNotNull(sessionCookie);
        mockMvc.perform(get("/api/v1/users/me").cookie(sessionCookie)).andExpect(status().isOk()).andExpect(jsonPath("$.data.username").value("csrfuser"));
        mockMvc.perform(post("/api/v1/auth/logout").cookie(sessionCookie, csrfCookie).header("X-XSRF-TOKEN", "wrong"))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        mockMvc.perform(post("/api/v1/auth/logout").cookie(sessionCookie, csrfCookie).header("X-XSRF-TOKEN", token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("OK"));
        assertFalse(csrfResult.getResponse().getContentAsString().contains("MvpTest-Only-123"));
    }
}
