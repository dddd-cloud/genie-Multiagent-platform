package com.jd.genie.platform.agentbridge;

import com.jd.genie.model.response.GptProcessResult;
import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.contract.UserRole;
import com.jd.genie.platform.contract.support.FakeConversationExecutionPort;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

final class ObserverTestSupport {
    static final CurrentUser USER = new CurrentUser(
            "tenant-default",
            "user-a-id",
            "user-a",
            "User A",
            UserRole.USER
    );
    static final String ASSISTANT_MESSAGE_ID = "assistant-1";

    private ObserverTestSupport() {
    }

    static ConversationStreamObserver observer(
            FakeConversationExecutionPort port,
            RecordingClientChannel channel
    ) {
        return observer(port, channel, SnapshotPruner.DEFAULT_MAX_BYTES, () -> {
        });
    }

    static ConversationStreamObserver observer(
            FakeConversationExecutionPort port,
            RecordingClientChannel channel,
            long maxSnapshotBytes,
            Runnable cancelAgentCall
    ) {
        return new ConversationStreamObserver(
                new StreamPersistenceObserver(port, USER, ASSISTANT_MESSAGE_ID),
                channel,
                maxSnapshotBytes,
                cancelAgentCall
        );
    }

    static GptProcessResult event(String response, boolean finished) {
        return GptProcessResult.builder()
                .status(finished ? "success" : "running")
                .response(response)
                .responseAll(response)
                .finished(finished)
                .responseType("text")
                .packageType("result")
                .build();
    }

    static GptProcessResult heartbeat() {
        return GptProcessResult.builder()
                .status("running")
                .response("")
                .responseAll("")
                .finished(false)
                .packageType("heartbeat")
                .build();
    }

    static final class RecordingClientChannel implements ConversationStreamObserver.ClientChannel {
        private final List<GptProcessResult> events = new CopyOnWriteArrayList<>();
        private final List<FailureSignal> failures = new CopyOnWriteArrayList<>();
        private final AtomicInteger completionCount = new AtomicInteger();
        private volatile Exception sendEventFailure;
        private volatile Exception sendFailureFailure;
        private volatile RuntimeException completeFailure;

        @Override
        public void sendEvent(GptProcessResult event) throws Exception {
            if (sendEventFailure != null) {
                throw sendEventFailure;
            }
            events.add(event);
        }

        @Override
        public void sendFailure(MvpErrorCode errorCode, String message) throws Exception {
            if (sendFailureFailure != null) {
                throw sendFailureFailure;
            }
            failures.add(new FailureSignal(errorCode, message));
        }

        @Override
        public void complete() {
            completionCount.incrementAndGet();
            if (completeFailure != null) {
                throw completeFailure;
            }
        }

        List<GptProcessResult> events() {
            return List.copyOf(events);
        }

        List<FailureSignal> failures() {
            return List.copyOf(failures);
        }

        int completionCount() {
            return completionCount.get();
        }

        void failEventSendWith(Exception failure) {
            this.sendEventFailure = failure;
        }

        void failFailureSendWith(Exception failure) {
            this.sendFailureFailure = failure;
        }

        void failCompletionWith(RuntimeException failure) {
            this.completeFailure = failure;
        }
    }

    record FailureSignal(MvpErrorCode errorCode, String message) {
    }
}
