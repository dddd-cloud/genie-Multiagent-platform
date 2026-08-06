package com.jd.genie.platform.phase2.runtime;

import com.jd.genie.model.req.GptQueryReq;
import com.jd.genie.platform.contract.ConversationExecutionResult;
import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.UserRole;
import com.jd.genie.platform.contract.support.FakeConversationExecutionPort;
import com.jd.genie.platform.contract.support.FakeCurrentUserProvider;
import com.jd.genie.service.IMultiAgentService;
import com.jd.genie.service.impl.GptProcessServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class GptProcessV1RegressionTest {
    private static final CurrentUser USER = new CurrentUser(
            "tenant-1", "user-1", "alice", "Alice", UserRole.USER
    );

    @Test
    void v1PublicEntryPreservesTheExistingAutoAgentLifecycle() {
        FakeConversationExecutionPort executionPort = new FakeConversationExecutionPort();
        executionPort.setPrepareExecutionResult(new ConversationExecutionResult(
                "123e4567-e89b-12d3-a456-426614174000",
                "request-1",
                "user-message-1",
                "assistant-message-1",
                1L
        ));
        IMultiAgentService autoAgent = mock(IMultiAgentService.class);
        GptProcessServiceImpl service = new GptProcessServiceImpl(
                autoAgent,
                new FakeCurrentUserProvider(USER),
                executionPort,
                3_600_000L,
                8_388_608L,
                6,
                12_000
        );

        service.queryMultiAgentIncrStream(GptQueryReq.builder()
                .sessionId("123e4567-e89b-12d3-a456-426614174000")
                .requestId("request-1")
                .query("question")
                .deepThink(0)
                .outputStyle("docs")
                .build());

        assertEquals(List.of(
                FakeConversationExecutionPort.CallType.PREPARE_EXECUTION,
                FakeConversationExecutionPort.CallType.LOAD_COMPLETED_HISTORY,
                FakeConversationExecutionPort.CallType.MARK_STREAMING
        ), executionPort.getCalls().stream().map(FakeConversationExecutionPort.CallRecord::type).toList());
        verify(autoAgent, times(1)).searchForAgentRequest(any(), any(), any());
    }
}
