package com.jd.genie.platform.contract;

import com.jd.genie.platform.contract.support.FakeConversationExecutionPort;
import com.jd.genie.platform.contract.support.FakeCurrentUserProvider;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Component;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContractFakeSupportTest {

    private static final CurrentUser USER_A = new CurrentUser(
        "tenant-default", "user-a-id", "user-a", "User A", UserRole.USER
    );
    private static final CurrentUser USER_B = new CurrentUser(
        "tenant-default", "user-b-id", "user-b", "User B", UserRole.USER
    );
    private static final CurrentUser ADMIN = new CurrentUser(
        "tenant-default", "admin-id", "admin", "Admin", UserRole.ADMIN
    );

    @Test
    void fakeCurrentUserProviderSwitchesUsers() {
        FakeCurrentUserProvider provider = new FakeCurrentUserProvider(USER_A);
        assertEquals(USER_A, provider.requireCurrentUser());

        provider.setCurrentUser(USER_B);
        assertEquals(USER_B, provider.requireCurrentUser());

        provider.setCurrentUser(ADMIN);
        assertEquals(ADMIN, provider.requireCurrentUser());
    }

    @Test
    void fakeCurrentUserProviderFailsWhenUnconfigured() {
        FakeCurrentUserProvider provider = new FakeCurrentUserProvider();
        assertThrows(IllegalStateException.class, provider::requireCurrentUser);
    }

    @Test
    void fakeConversationExecutionPortRecordsCallsInOrder() {
        FakeConversationExecutionPort port = new FakeConversationExecutionPort();
        ConversationExecutionResult result = new ConversationExecutionResult(
            "conv-1", "req-1", "user-msg-1", "asst-msg-1", 1L
        );
        port.setPrepareExecutionResult(result);
        port.setLoadCompletedHistoryResult(List.of(
            new ConversationHistoryItem(1L, ConversationMessageRole.USER, "hello")
        ));

        ConversationExecutionCommand command = new ConversationExecutionCommand(
            "conv-1", "req-1", "hello", 0, "docs"
        );
        port.prepareExecution(USER_A, command);
        port.markStreaming(USER_A, "asst-msg-1");
        port.complete(USER_A, new MessageCompletionCommand(
            "asst-msg-1", "done", "{}", 1
        ));
        port.fail(USER_A, new MessageFailureCommand(
            "asst-msg-1", "INTERNAL_ERROR", "err", null, null
        ));
        port.interrupt(USER_A, new MessageFailureCommand(
            "asst-msg-1", "CLIENT_DISCONNECTED", "dc", null, null
        ));
        port.loadCompletedHistory(USER_A, "conv-1", "req-1", 6, 12000);

        List<FakeConversationExecutionPort.CallRecord> calls = port.getCalls();
        assertEquals(6, calls.size());
        assertEquals(FakeConversationExecutionPort.CallType.PREPARE_EXECUTION, calls.get(0).type());
        assertEquals(FakeConversationExecutionPort.CallType.MARK_STREAMING, calls.get(1).type());
        assertEquals(FakeConversationExecutionPort.CallType.COMPLETE, calls.get(2).type());
        assertEquals(FakeConversationExecutionPort.CallType.FAIL, calls.get(3).type());
        assertEquals(FakeConversationExecutionPort.CallType.INTERRUPT, calls.get(4).type());
        assertEquals(FakeConversationExecutionPort.CallType.LOAD_COMPLETED_HISTORY, calls.get(5).type());
        assertEquals(USER_A, calls.get(0).currentUser());
        assertEquals(command, calls.get(0).command());
    }

    @Test
    void fakeConversationExecutionPortResetClearsCalls() {
        FakeConversationExecutionPort port = new FakeConversationExecutionPort();
        port.setPrepareExecutionResult(new ConversationExecutionResult(
            "conv-1", "req-1", "user-msg-1", "asst-msg-1", 1L
        ));
        port.prepareExecution(USER_A, new ConversationExecutionCommand(
            "conv-1", "req-1", "hello", 0, "docs"
        ));
        assertFalse(port.getCalls().isEmpty());

        port.reset();
        assertTrue(port.getCalls().isEmpty());
    }

    @Test
    void fakeClassesHaveNoSpringBeanAnnotations() {
        assertFalse(FakeCurrentUserProvider.class.isAnnotationPresent(Component.class));
        assertFalse(FakeConversationExecutionPort.class.isAnnotationPresent(Component.class));
    }
}
