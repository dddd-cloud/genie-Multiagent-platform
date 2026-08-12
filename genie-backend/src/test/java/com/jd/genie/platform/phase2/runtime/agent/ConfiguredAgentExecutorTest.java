package com.jd.genie.platform.phase2.runtime.agent;

import com.jd.genie.agent.agent.AgentContext;
import com.jd.genie.agent.agent.ReactImplAgent;
import com.jd.genie.agent.printer.Printer;
import com.jd.genie.platform.agentbridge.AgentBridgeException;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2contract.dto.AgentRuntimeProfile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConfiguredAgentExecutorTest {

    @Test
    void plainTextDoesNotBecomeSuccess() {
        AgentBridgeException error = assertThrows(
                AgentBridgeException.class,
                () -> executeRaw("done")
        );
        assertEquals(MvpErrorCode.AGENT_INVALID_RESULT, error.getErrorCode());
    }

    @Test
    void malformedJsonDoesNotBecomeSuccess() {
        AgentBridgeException error = assertThrows(
                AgentBridgeException.class,
                () -> executeRaw("{\"status\":\"SUCCESS\",\"output\":")
        );
        assertEquals(MvpErrorCode.AGENT_INVALID_RESULT, error.getErrorCode());
    }

    @Test
    void validJsonBecomesSuccess() {
        AgentTaskResult result = executeRaw(
                "{\"status\":\"SUCCESS\",\"output\":\"done\",\"errorCode\":null,\"retryable\":false}"
        );
        assertEquals(AgentTaskResult.Status.SUCCESS, result.status());
        assertEquals("done", result.output());
    }

    @Test
    void fencedEnvelopeStillSucceedsViaParser() {
        AgentTaskResult result = executeRaw("""
                ```json
                {"status":"SUCCESS","output":"done","errorCode":null,"retryable":false}
                ```
                """);
        assertEquals(AgentTaskResult.Status.SUCCESS, result.status());
        assertEquals("done", result.output());
    }

    private AgentTaskResult executeRaw(String raw) {
        ConfiguredReactAgentFactory factory = mock(ConfiguredReactAgentFactory.class);
        ReactImplAgent agent = mock(ReactImplAgent.class);
        when(factory.create(any(), any(), any(), anyInt())).thenReturn(agent);
        when(agent.run(any())).thenReturn(raw);

        ConfiguredAgentExecutor executor = new ConfiguredAgentExecutor(factory, new AgentTaskResultParser());
        AgentContext context = mock(AgentContext.class);
        when(context.getQuery()).thenReturn("q");
        return executor.execute(context, mock(AgentRuntimeProfile.class), mock(Printer.class), 3);
    }
}
