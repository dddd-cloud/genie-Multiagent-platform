package com.jd.genie.platform.phase2.tooling;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.platform.contract.CurrentUser;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class McpToolInputValidationTest {
    @Test void requiredPropertyIsValidatedBeforeTransport() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class); McpClientAdapter client = mock(McpClientAdapter.class); ObjectMapper mapper = new ObjectMapper();
        var schema = mapper.createObjectNode(); schema.putArray("required").add("q"); schema.putObject("properties").putObject("q").put("type", "string");
        var adapter = new UserMcpToolAdapter(jdbc, client, mock(CredentialEnvelopeService.class), mock(McpUrlPolicy.class), mapper, new CurrentUser("t", "o", "u", "U", null), "tool", "server", "mcp:tool", "mcp_tool", "d", schema);
        assertThatThrownBy(() -> adapter.execute(Map.of())).hasMessageContaining("tool input invalid");
        verifyNoInteractions(jdbc, client);
    }
    @Test void argumentLimitIsEnforced() {
        ObjectMapper mapper = new ObjectMapper(); JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var adapter = new UserMcpToolAdapter(jdbc, mock(McpClientAdapter.class), mock(CredentialEnvelopeService.class), mock(McpUrlPolicy.class), mapper, new CurrentUser("t", "o", "u", "U", null), "tool", "server", "mcp:tool", "mcp_tool", "d", mapper.createObjectNode());
        assertThatThrownBy(() -> adapter.execute(Map.of("large", "x".repeat(300_000)))).hasMessageContaining("tool input invalid");
        verifyNoInteractions(jdbc);
    }
}
