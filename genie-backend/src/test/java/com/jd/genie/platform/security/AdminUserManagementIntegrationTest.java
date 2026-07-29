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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(classes = Phase3IntegrationTestApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AdminUserManagementIntegrationTest {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36").withDatabaseName("genie").withUsername("test").withPassword("test");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl); registry.add("spring.datasource.username", MYSQL::getUsername); registry.add("spring.datasource.password", MYSQL::getPassword);
    }
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantService tenantService;
    @Autowired UserService userService;
    @Autowired UserMapper userMapper;

    @BeforeEach void users() {
        var tenant = tenantService.ensureDefaultTenant();
        ensure(tenant.getId(), "adminfour", UserRole.ADMIN); ensure(tenant.getId(), "userfour", UserRole.USER);
    }
    @Test void adminEndpointsEnforceRoleCsrfValidationAndTenantScopedMutation() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
        Login user = login("userfour");
        mockMvc.perform(get("/api/v1/admin/users").cookie(user.session())).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        Login admin = login("adminfour");
        mockMvc.perform(post("/api/v1/admin/users").cookie(admin.session()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"newadminuser\",\"displayName\":\"新用户\",\"password\":\"MvpTest-Only-123\",\"role\":\"USER\"}"))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        MvcResult created = mockMvc.perform(post("/api/v1/admin/users").cookie(admin.session(), admin.csrf()).header("X-XSRF-TOKEN", admin.token()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\" NewAdminUser \",\"displayName\":\"新用户\",\"password\":\"MvpTest-Only-123\",\"role\":\"USER\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.username").value("newadminuser")).andExpect(jsonPath("$.data.status").value("ACTIVE")).andReturn();
        String id = objectMapper.readTree(created.getResponse().getContentAsString()).path("data").path("id").asText();
        mockMvc.perform(get("/api/v1/admin/users").cookie(admin.session())).andExpect(status().isOk()).andExpect(jsonPath("$.data.page").value(1));
        mockMvc.perform(patch("/api/v1/admin/users/{id}/status", id).cookie(admin.session(), admin.csrf()).header("X-XSRF-TOKEN", admin.token()).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DISABLED\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("DISABLED"));
        mockMvc.perform(patch("/api/v1/admin/users/{id}/status", id).cookie(admin.session(), admin.csrf()).header("X-XSRF-TOKEN", admin.token()).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACTIVE\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("ACTIVE"));
        mockMvc.perform(post("/api/v1/admin/users").cookie(admin.session(), admin.csrf()).header("X-XSRF-TOKEN", admin.token()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"newadminuser\",\"displayName\":\"Duplicate\",\"password\":\"MvpTest-Only-123\",\"role\":\"USER\"}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("USER_ALREADY_EXISTS"));
        mockMvc.perform(patch("/api/v1/admin/users/{id}/status", "missing-user").cookie(admin.session(), admin.csrf()).header("X-XSRF-TOKEN", admin.token()).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DISABLED\"}"))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(post("/api/v1/admin/users/{id}/reset-password", id).cookie(admin.session(), admin.csrf()).header("X-XSRF-TOKEN", admin.token()).contentType(MediaType.APPLICATION_JSON).content("{\"newPassword\":\"MvpTest-Only-456\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data").doesNotExist());
        assertFalse(created.getResponse().getContentAsString().contains("passwordHash"));
    }
    private void ensure(String tenantId, String username, UserRole role) { if (userMapper.findByTenantIdAndUsername(tenantId, username) == null) userService.createUser(new CreateUserCommand(tenantId, username, username, "MvpTest-Only-123", role, UserStatus.ACTIVE)); }
    private Login login(String username) throws Exception {
        MvcResult csrf = mockMvc.perform(get("/api/v1/auth/csrf")).andReturn();
        var csrfCookie = csrf.getResponse().getCookie("XSRF-TOKEN"); assertNotNull(csrfCookie);
        String token = objectMapper.readTree(csrf.getResponse().getContentAsString()).path("data").path("token").asText();
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login").cookie(csrfCookie).header("X-XSRF-TOKEN", token).contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"" + username + "\",\"password\":\"MvpTest-Only-123\"}"))
            .andExpect(status().isOk()).andReturn();
        return new Login(login.getResponse().getCookie("GENIE_SESSION"), csrfCookie, token);
    }
    private record Login(jakarta.servlet.http.Cookie session, jakarta.servlet.http.Cookie csrf, String token) { }
}
