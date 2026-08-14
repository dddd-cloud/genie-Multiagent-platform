package com.jd.genie.platform.phase2.skillruntime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.agent.agent.AgentContext;
import com.jd.genie.agent.tool.BaseTool;
import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.phase2.tooling.*;
import com.jd.genie.platform.phase2contract.capability.CapabilityKeys;
import com.jd.genie.platform.phase2contract.dto.AgentRuntimeProfile;
import com.jd.genie.platform.phase2contract.dto.ToolBindingView;
import com.jd.genie.platform.phase2contract.port.ToolBindingPort;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RuntimeNameCollisionTest {
    @Test
    void skillAdditionalToolCollidingWithBuiltinFailsClosedBeforeCollectionIsPublished() {
        AgentContext context = AgentContext.builder().requestId("collision").build();
        BuiltinCapabilityCatalog catalog = new BuiltinCapabilityCatalog();
        BaseTool builtin = catalog.create(context).get(CapabilityKeys.BUILTIN_DEEP_SEARCH);
        BaseTool skillTool = tool(builtin.getName());
        ToolBindingPort bindings = mock(ToolBindingPort.class);
        when(bindings.resolveBindings(any(), eq("agent"), any()))
            .thenReturn(new ToolBindingView(List.of(CapabilityKeys.BUILTIN_DEEP_SEARCH), Map.of(), List.of()));
        RuntimeToolCollectionService service = new RuntimeToolCollectionService(bindings, catalog,
            mock(JdbcTemplate.class), mock(McpClientAdapter.class), mock(CredentialEnvelopeService.class),
            mock(McpUrlPolicy.class), new ObjectMapper());
        AgentRuntimeProfile profile = new AgentRuntimeProfile("agent", 1, "a", "", "", "",
            List.of(), List.of(CapabilityKeys.BUILTIN_DEEP_SEARCH));

        assertThatThrownBy(() -> service.build(
            new CurrentUser("tenant", "owner", "owner", "Owner", null), profile, context, List.of(skillTool)))
            .isInstanceOf(ToolCapabilityException.class)
            .hasMessageContaining("runtime name conflict");
        org.junit.jupiter.api.Assertions.assertNull(context.getToolCollection());
    }

    private BaseTool tool(String name) {
        return new BaseTool() {
            public String getName() { return name; }
            public String getDescription() { return "Skill test tool"; }
            public Map<String, Object> toParams() { return Map.of("type", "object"); }
            public Object execute(Object input) { return input; }
        };
    }
}
