package com.jd.genie.platform.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(classes = Phase3IntegrationTestApplication.class, properties = "GENIE_INTERNAL_AGENT_TOKEN=phase5-test-token")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class InternalAgentSecurityIntegrationTest {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36").withDatabaseName("genie").withUsername("test").withPassword("test");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl); registry.add("spring.datasource.username", MYSQL::getUsername); registry.add("spring.datasource.password", MYSQL::getPassword);
    }
    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test void autoAgentRequiresItsOwnTokenIgnoresCsrfOnlyThereAndDoesNotCreateSession() throws Exception {
        Integer sessionsBefore = jdbcTemplate.queryForObject("select count(*) from SPRING_SESSION", Integer.class);
        MockHttpSession browserSession = new MockHttpSession();
        mockMvc.perform(post("/AutoAgent").session(browserSession).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INTERNAL_TOKEN_INVALID"));
        mockMvc.perform(post("/AutoAgent").header(InternalAgentAuthFilter.HEADER, "wrong").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INTERNAL_TOKEN_INVALID"));
        var entered = mockMvc.perform(post("/AutoAgent").header(InternalAgentAuthFilter.HEADER, "phase5-test-token")
                .contentType(MediaType.APPLICATION_JSON).content("{"))
            .andExpect(status().isBadRequest()).andReturn().getResponse();
        org.junit.jupiter.api.Assertions.assertNotEquals(401, entered.getStatus());
        org.junit.jupiter.api.Assertions.assertNotEquals(403, entered.getStatus());
        org.junit.jupiter.api.Assertions.assertFalse(entered.getContentAsString().contains("INTERNAL_TOKEN_INVALID"));
        assertNull(entered.getCookie("GENIE_SESSION"));
        assertEquals(sessionsBefore, jdbcTemplate.queryForObject("select count(*) from SPRING_SESSION", Integer.class));

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        mockMvc.perform(get("/api/v1/users/me").header(InternalAgentAuthFilter.HEADER, "phase5-test-token"))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }
}
