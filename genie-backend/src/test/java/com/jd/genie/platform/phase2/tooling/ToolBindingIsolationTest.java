package com.jd.genie.platform.phase2.tooling;

import com.jd.genie.platform.contract.CurrentUser;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ToolBindingIsolationTest {
    @Test
    void emptyReplacementDeletesOnlyScopedRows() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ToolBindingService service = new ToolBindingService(jdbc);
        service.replaceAgentBindings(new CurrentUser("tenant-a", "owner-a", "u", "U", null), "agent-a", List.of());
        verify(jdbc).update(contains("tenant_id=? AND owner_id=? AND agent_id=?"), eq("tenant-a"), eq("owner-a"), eq("agent-a"));
        verifyNoMoreInteractions(jdbc);
    }
}
