package com.jd.genie.platform.agentbridge;

import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.MessageCompletionCommand;
import com.jd.genie.platform.contract.MessageFailureCommand;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.contract.UserRole;
import com.jd.genie.platform.contract.support.FakeConversationExecutionPort;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StreamPersistenceObserverTest {

    private static final CurrentUser USER = new CurrentUser(
            "tenant-default",
            "user-a-id",
            "user-a",
            "User A",
            UserRole.USER
    );

    @Test
    void delegatesFrozenLifecycleCommandsWithStableIdentity() {
        FakeConversationExecutionPort port = new FakeConversationExecutionPort();
        StreamPersistenceObserver observer = new StreamPersistenceObserver(port, USER, "assistant-1");

        observer.markStreaming();
        observer.complete("最终回答", "{\"payloadVersion\":1}", 1);
        observer.fail(MvpErrorCode.AGENT_DOWNSTREAM_ERROR, "downstream", "{}", 1);
        observer.interrupt(MvpErrorCode.CLIENT_DISCONNECTED, "disconnected", null, null);

        List<FakeConversationExecutionPort.CallRecord> calls = port.getCalls();
        assertEquals(List.of(
                FakeConversationExecutionPort.CallType.MARK_STREAMING,
                FakeConversationExecutionPort.CallType.COMPLETE,
                FakeConversationExecutionPort.CallType.FAIL,
                FakeConversationExecutionPort.CallType.INTERRUPT
        ), calls.stream().map(FakeConversationExecutionPort.CallRecord::type).toList());
        calls.forEach(call -> assertEquals(USER, call.currentUser()));
        calls.forEach(call -> assertEquals("assistant-1", call.assistantMessageId()));

        MessageCompletionCommand completion = calls.get(1).completionCommand();
        assertEquals("最终回答", completion.finalContent());
        assertEquals("{\"payloadVersion\":1}", completion.snapshotJson());
        assertEquals(1, completion.payloadVersion());

        MessageFailureCommand failure = calls.get(2).failureCommand();
        assertEquals("AGENT_DOWNSTREAM_ERROR", failure.errorCode());
        assertEquals("downstream", failure.errorMessage());
        assertEquals("{}", failure.partialSnapshotJson());
        assertEquals(1, failure.payloadVersion());

        MessageFailureCommand interruption = calls.get(3).failureCommand();
        assertEquals("CLIENT_DISCONNECTED", interruption.errorCode());
        assertEquals("disconnected", interruption.errorMessage());
        assertEquals(null, interruption.partialSnapshotJson());
        assertEquals(null, interruption.payloadVersion());
    }

    @Test
    void rejectsInvalidRequiredCommandDataBeforeCallingPort() {
        FakeConversationExecutionPort port = new FakeConversationExecutionPort();

        assertThrows(
                IllegalArgumentException.class,
                () -> new StreamPersistenceObserver(port, USER, " ")
        );

        StreamPersistenceObserver observer = new StreamPersistenceObserver(port, USER, "assistant-1");
        assertThrows(IllegalArgumentException.class, () -> observer.complete(" ", "{}", 1));
        assertThrows(IllegalArgumentException.class, () -> observer.complete("answer", " ", 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> observer.fail(MvpErrorCode.INTERNAL_ERROR, " ", null, null)
        );
        assertEquals(List.of(), port.getCalls());
    }
}
