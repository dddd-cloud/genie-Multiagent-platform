package com.jd.genie.platform.phase2.tooling;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.platform.contract.CurrentUser;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class UserMcpToolAdapterTest {
    @Test void invalidInputNeverCallsMcpOrDatabase() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class); McpClientAdapter client = mock(McpClientAdapter.class);
        ObjectMapper mapper = new ObjectMapper(); var schema = mapper.createObjectNode(); schema.putObject("properties").putObject("x").put("type", "string");
        var adapter = new UserMcpToolAdapter(jdbc, client, mock(CredentialEnvelopeService.class), mock(McpUrlPolicy.class), mapper, new CurrentUser("t", "o", "u", "U", null), "tool", "server", "mcp:tool", "mcp_tool", "d", schema);
        assertThatThrownBy(() -> adapter.execute(Map.of("x", 1))).hasMessageContaining("tool input invalid");
        verifyNoInteractions(jdbc, client);
    }
}
