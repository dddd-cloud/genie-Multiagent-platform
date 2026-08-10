package com.jd.genie.platform.phase2.tooling;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.platform.contract.CurrentUser;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class UserMcpToolAdapterTest {
    @Test void invalidInputNeverCallsMcpOrDatabase() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class); McpClientAdapter client = mock(McpClientAdapter.class);
        ObjectMapper mapper = new ObjectMapper(); var schema = mapper.createObjectNode(); schema.putObject("properties").putObject("x").put("type", "string");
        var adapter = new UserMcpToolAdapter(jdbc, client, mock(CredentialEnvelopeService.class), mock(McpUrlPolicy.class), mapper, new CurrentUser("t", "o", "u", "U", null), "tool", "server", "mcp:tool", "mcp_tool", "remote_tool", "d", schema);
        assertThatThrownBy(() -> adapter.execute(Map.of("x", 1))).hasMessageContaining("tool input invalid");
        verifyNoInteractions(jdbc, client);
    }

    @Test void summarizeTicketResultBuildsSortedScheduleWithDepartures() {
        String raw = """
                G3821 南京南(telecode:NKH) -> 上海(telecode:SHH) 14:15 -> 16:16
                G2421 南京南(telecode:NKH) -> 上海虹桥(telecode:AOH) 12:05 -> 13:40
                G2421 南京南(telecode:NKH) -> 上海松江(telecode:IMH) 12:05 -> 13:20
                note: ignore
                """;
        String summary = UserMcpToolAdapter.summarizeTicketResult(raw);
        assertThat(summary).contains("uniqueTrainCount=2");
        assertThat(summary).contains("G2421|12:05|13:40|南京南->上海虹桥");
        assertThat(summary).contains("G3821|14:15|16:16|南京南->上海");
        assertThat(summary.indexOf("G2421|")).isLessThan(summary.indexOf("G3821|"));
        assertThat(summary).contains("禁止再次全量查询");
        assertThat(summary).contains("把 schedule 写入 SUCCESS output");
    }
}
