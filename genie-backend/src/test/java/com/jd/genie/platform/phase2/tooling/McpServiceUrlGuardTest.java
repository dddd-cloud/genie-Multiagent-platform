package com.jd.genie.platform.phase2.tooling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.CurrentUserProvider;
import com.jd.genie.platform.contract.UserRole;
import java.sql.ResultSet;
import java.time.Clock;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.mock.env.MockEnvironment;

class McpServiceUrlGuardTest {
    @Test
    void testRejectsDangerousUrlBeforeFakeAdapter() {
        var calls = new AtomicInteger();
        var adapter = new McpClientAdapter() {
            public java.util.List<RemoteTool> listTools(String u, AuthType t, String n, String c) { calls.incrementAndGet(); return java.util.List.of(); }
            public com.fasterxml.jackson.databind.JsonNode callTool(String u, AuthType t, String n, String c, String tool, java.util.Map<String,Object> a) { calls.incrementAndGet(); return null; }
        };
        var service = service(adapter, "http://127.0.0.1:8080/mcp");
        assertThatThrownBy(() -> service.test("server")).hasMessageContaining("MCP URL rejected");
        assertThat(calls).hasValue(0);
    }

    @Test
    void refreshRejectsDangerousUrlBeforeFakeAdapter() {
        var calls = new AtomicInteger();
        var adapter = new FakeMcpClientAdapter() {
            @Override public java.util.List<RemoteTool> listTools(String u, AuthType t, String n, String c) { calls.incrementAndGet(); return super.listTools(u,t,n,c); }
        };
        var service = service(adapter, "http://127.0.0.1:8080/mcp");
        assertThatThrownBy(() -> service.refreshTools("server")).hasMessageContaining("MCP URL rejected");
        assertThat(calls).hasValue(0);
    }

    private McpServerService service(McpClientAdapter adapter, String url) {
        var jdbc = mock(JdbcTemplate.class);
        var user = (CurrentUserProvider) () -> new CurrentUser("tenant", "owner", "u", "U", UserRole.ADMIN);
        when(jdbc.queryForObject(org.mockito.ArgumentMatchers.startsWith("SELECT server_url"), org.mockito.ArgumentMatchers.eq(String.class), org.mockito.ArgumentMatchers.any(Object[].class))).thenReturn(url);
        when(jdbc.queryForObject(org.mockito.ArgumentMatchers.startsWith("SELECT credential_envelope"), org.mockito.ArgumentMatchers.any(RowMapper.class), org.mockito.ArgumentMatchers.any(Object[].class))).thenAnswer(inv -> {
            ResultSet rs = mock(ResultSet.class);
            when(rs.getString(1)).thenReturn(null); when(rs.getString(2)).thenReturn(null); when(rs.getString(3)).thenReturn("NONE"); when(rs.getString(4)).thenReturn(null);
            return ((RowMapper<?>) inv.getArgument(1)).mapRow(rs, 1);
        });
        return new McpServerService(jdbc, user, mock(CredentialEnvelopeService.class), new com.fasterxml.jackson.databind.ObjectMapper(), Clock.systemUTC(), adapter, new McpUrlPolicy(new MockEnvironment().withProperty("spring.profiles.active", "test")));
    }
}
