package com.jd.genie.platform.phase2.tooling;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.agent.agent.AgentContext;
import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.phase2contract.capability.CapabilityKeys;
import com.jd.genie.platform.phase2contract.dto.AgentRuntimeProfile;
import com.jd.genie.platform.phase2contract.dto.ToolBindingView;
import com.jd.genie.platform.phase2contract.port.ToolBindingPort;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class RuntimeToolCollectionPortTest {
    @Test
    void buildsEmptyAuthorizedCollectionWithoutNetworkOrCredentialAccess() {
        ToolBindingPort binding = mock(ToolBindingPort.class);
        when(binding.resolveBindings(any(), eq("agent"), any())).thenReturn(new ToolBindingView(List.of(), java.util.Map.of(), List.of()));
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        RuntimeToolCollectionService service = new RuntimeToolCollectionService(binding, new BuiltinCapabilityCatalog(), jdbc, mock(McpClientAdapter.class), mock(CredentialEnvelopeService.class), mock(McpUrlPolicy.class), new ObjectMapper());
        AgentContext context = AgentContext.builder().requestId("r").build();
        var result = service.build(new CurrentUser("t", "o", "u", "U", null), new AgentRuntimeProfile("agent", 1, "a", "", "", "", List.of(), List.of()), context);
        assertThat(result).isInstanceOf(AuthorizedToolCollection.class);
        assertThat(context.getToolCollection()).isSameAs(result);
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectsNullInputs() {
        RuntimeToolCollectionService service = new RuntimeToolCollectionService(mock(ToolBindingPort.class), new BuiltinCapabilityCatalog(), mock(JdbcTemplate.class), mock(McpClientAdapter.class), mock(CredentialEnvelopeService.class), mock(McpUrlPolicy.class), new ObjectMapper());
        assertThatThrownBy(() -> service.build(null, null, null)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void replacesBackendPythonToolsWhenBrowserWorkspaceIsMounted() {
        ToolBindingPort binding = mock(ToolBindingPort.class);
        List<String> capabilities = List.of(
                CapabilityKeys.BUILTIN_CODE_INTERPRETER,
                CapabilityKeys.BUILTIN_DATA_ANALYSIS,
                CapabilityKeys.BUILTIN_DEEP_SEARCH
        );
        when(binding.resolveBindings(any(), eq("agent"), any()))
                .thenReturn(new ToolBindingView(capabilities, java.util.Map.of(), List.of()));
        RuntimeToolCollectionService service = new RuntimeToolCollectionService(
                binding,
                new BuiltinCapabilityCatalog(),
                mock(JdbcTemplate.class),
                mock(McpClientAdapter.class),
                mock(CredentialEnvelopeService.class),
                mock(McpUrlPolicy.class),
                new ObjectMapper()
        );
        AgentContext context = AgentContext.builder()
                .requestId("r")
                .query("[UNTRUSTED_BROWSER_WORKSPACE]\n文件: /workspace/test.py")
                .build();
        AgentRuntimeProfile profile = new AgentRuntimeProfile(
                "agent", 1, "a", "", "", "", List.of(), capabilities
        );

        var result = (AuthorizedToolCollection) service.build(
                new CurrentUser("t", "o", "u", "U", null),
                profile,
                context
        );

        assertThat(result.authorizedTools().keySet())
                .contains("deep_search")
                .doesNotContain("code_interpreter", "data_analysis");
    }
}
