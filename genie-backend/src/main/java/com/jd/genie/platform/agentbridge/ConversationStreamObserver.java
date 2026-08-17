package com.jd.genie.platform.agentbridge;

import com.jd.genie.model.response.GptProcessResult;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.contract.StreamSnapshotEnvelope;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public final class ConversationStreamObserver {
    private static final String DEFAULT_DOWNSTREAM_MESSAGE = "Agent downstream stream failed";
    private static final String CLIENT_DISCONNECTED_MESSAGE = "Client disconnected before the Agent stream completed";
    private static final int MAX_ERROR_MESSAGE_LENGTH = 1_000;

    private final Object coordinationLock = new Object();
    private final StreamPersistenceObserver persistence;
    private final StreamSnapshotBuffer snapshotBuffer;
    private final SnapshotPruner snapshotPruner;
    private final FinalAnswerExtractor finalAnswerExtractor;
    private final ClientChannel clientChannel;
    private final Runnable cancelAgentCall;
    private final long maxSnapshotBytes;
    private final AtomicReference<TerminalState> state = new AtomicReference<>(TerminalState.OPEN);
    private final AtomicBoolean streamingMarked = new AtomicBoolean(false);

    public ConversationStreamObserver(
            StreamPersistenceObserver persistence,
            ClientChannel clientChannel
    ) {
        this(
                persistence,
                clientChannel,
                SnapshotPruner.DEFAULT_MAX_BYTES,
                () -> {
                }
        );
    }

    public ConversationStreamObserver(
            StreamPersistenceObserver persistence,
            ClientChannel clientChannel,
            long maxSnapshotBytes,
            Runnable cancelAgentCall
    ) {
        if (maxSnapshotBytes <= 0) {
            throw new IllegalArgumentException("maxSnapshotBytes must be positive");
        }
        this.persistence = Objects.requireNonNull(persistence, "persistence");
        this.clientChannel = Objects.requireNonNull(clientChannel, "clientChannel");
        this.cancelAgentCall = Objects.requireNonNull(cancelAgentCall, "cancelAgentCall");
        this.maxSnapshotBytes = maxSnapshotBytes;
        this.snapshotBuffer = new StreamSnapshotBuffer();
        this.snapshotPruner = new SnapshotPruner();
        this.finalAnswerExtractor = new FinalAnswerExtractor();
    }

    public boolean markStreaming() {
        synchronized (coordinationLock) {
            if (state.get() != TerminalState.OPEN || !streamingMarked.compareAndSet(false, true)) {
                return false;
            }
            try {
                persistence.markStreaming();
                return true;
            } catch (RuntimeException | Error error) {
                streamingMarked.set(false);
                throw error;
            }
        }
    }

    public boolean onEvent(GptProcessResult event) {
        synchronized (coordinationLock) {
            if (state.get() != TerminalState.OPEN) {
                return false;
            }
            try {
                snapshotBuffer.append(event);
            } catch (Throwable error) {
                return transitionToFailure(failureOf(error, MvpErrorCode.SNAPSHOT_INVALID));
            }
            try {
                clientChannel.sendEvent(event);
                return true;
            } catch (Throwable error) {
                return transitionToInterrupted(
                        new Failure(MvpErrorCode.CLIENT_DISCONNECTED, messageOf(error, CLIENT_DISCONNECTED_MESSAGE))
                );
            }
        }
    }

    /**
     * Live-trace helper: never mark the stream CLIENT_DISCONNECTED on send failure.
     * Critical orchestration/result events must still use {@link #onEvent}.
     */
    public boolean onEventBestEffort(GptProcessResult event) {
        synchronized (coordinationLock) {
            if (state.get() != TerminalState.OPEN) {
                return false;
            }
            try {
                snapshotBuffer.append(event);
            } catch (Throwable error) {
                log.warn("best-effort snapshot append failed: {}", error.toString());
                return false;
            }
            try {
                clientChannel.sendEvent(event);
                return true;
            } catch (Throwable error) {
                log.warn("best-effort SSE send failed: {}", error.toString());
                return false;
            }
        }
    }

    public boolean onCompleted() {
        synchronized (coordinationLock) {
            if (state.get() != TerminalState.OPEN) {
                return false;
            }

            Completion completion;
            try {
                StreamSnapshotEnvelope snapshot = snapshotBuffer.snapshot();
                completion = new Completion(
                        finalAnswerExtractor.extract(snapshot.events()),
                        snapshotPruner.serialize(snapshot, maxSnapshotBytes),
                        snapshot.payloadVersion()
                );
            } catch (Throwable error) {
                return transitionToFailure(failureOf(error, MvpErrorCode.SNAPSHOT_INVALID));
            }

            if (!state.compareAndSet(TerminalState.OPEN, TerminalState.COMPLETED)) {
                return false;
            }
            try {
                persistence.complete(
                        completion.finalContent(),
                        completion.snapshotJson(),
                        completion.payloadVersion()
                );
            } catch (Throwable error) {
                state.set(TerminalState.FAILED);
                persistFailure(failureOf(error, MvpErrorCode.SNAPSHOT_INVALID), true);
                return true;
            }
            completeClientChannel();
            return true;
        }
    }

    public boolean onError(Throwable error) {
        synchronized (coordinationLock) {
            return transitionToFailure(failureOf(error, MvpErrorCode.AGENT_DOWNSTREAM_ERROR));
        }
    }

    public boolean onClientDisconnected() {
        synchronized (coordinationLock) {
            return transitionToInterrupted(
                    new Failure(MvpErrorCode.CLIENT_DISCONNECTED, CLIENT_DISCONNECTED_MESSAGE)
            );
        }
    }

    public TerminalState state() {
        return state.get();
    }

    public boolean isTerminal() {
        return state.get() != TerminalState.OPEN;
    }

    public boolean isStreamingMarked() {
        return streamingMarked.get();
    }

    public int bufferedEventCount() {
        return snapshotBuffer.size();
    }

    private boolean transitionToFailure(Failure failure) {
        if (!state.compareAndSet(TerminalState.OPEN, TerminalState.FAILED)) {
            return false;
        }
        persistFailure(failure, true);
        return true;
    }

    private boolean transitionToInterrupted(Failure failure) {
        if (!state.compareAndSet(TerminalState.OPEN, TerminalState.INTERRUPTED)) {
            return false;
        }
        cancelAgentCall();
        PartialSnapshot partialSnapshot = partialSnapshot();
        trySendFailure(failure);
        try {
            persistence.interrupt(
                    failure.errorCode(),
                    failure.message(),
                    partialSnapshot.json(),
                    partialSnapshot.payloadVersion()
            );
        } finally {
            completeClientChannel();
        }
        return true;
    }

    private void persistFailure(Failure failure, boolean cancelCall) {
        if (cancelCall) {
            cancelAgentCall();
        }
        PartialSnapshot partialSnapshot = partialSnapshot();
        trySendFailure(failure);
        try {
            persistence.fail(
                    failure.errorCode(),
                    failure.message(),
                    partialSnapshot.json(),
                    partialSnapshot.payloadVersion()
            );
        } finally {
            completeClientChannel();
        }
    }

    private PartialSnapshot partialSnapshot() {
        try {
            return new PartialSnapshot(
                    snapshotPruner.serialize(snapshotBuffer.snapshot(), maxSnapshotBytes),
                    StreamSnapshotBuffer.PAYLOAD_VERSION
            );
        } catch (RuntimeException error) {
            log.warn("Partial snapshot serialization failed, persisting without snapshot: {}", error.getMessage(), error);
            return new PartialSnapshot(null, null);
        }
    }

    private Failure failureOf(Throwable error, MvpErrorCode fallbackCode) {
        MvpErrorCode errorCode = AgentBridgeErrorMapper.errorCode(error, fallbackCode);
        return new Failure(errorCode, messageOf(error, defaultMessage(errorCode)));
    }

    private String messageOf(Throwable error, String fallback) {
        String message = error == null ? null : error.getMessage();
        String safeMessage = message == null || message.isBlank() ? fallback : message;
        return safeMessage.length() <= MAX_ERROR_MESSAGE_LENGTH
                ? safeMessage
                : safeMessage.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }

    private String defaultMessage(MvpErrorCode errorCode) {
        return errorCode == MvpErrorCode.AGENT_DOWNSTREAM_ERROR
                ? DEFAULT_DOWNSTREAM_MESSAGE
                : errorCode.name();
    }

    private void trySendFailure(Failure failure) {
        try {
            clientChannel.sendFailure(failure.errorCode(), failure.message());
        } catch (Exception ignored) {
        }
    }

    private void cancelAgentCall() {
        try {
            cancelAgentCall.run();
        } catch (RuntimeException ignored) {
        }
    }

    private void completeClientChannel() {
        try {
            clientChannel.complete();
        } catch (RuntimeException ignored) {
        }
    }

    public enum TerminalState {
        OPEN,
        COMPLETED,
        FAILED,
        INTERRUPTED
    }

    @FunctionalInterface
    public interface ClientChannel {
        void sendEvent(GptProcessResult event) throws Exception;

        default void sendFailure(MvpErrorCode errorCode, String message) throws Exception {
        }

        default void complete() {
        }
    }

    private record Completion(String finalContent, String snapshotJson, int payloadVersion) {
    }

    private record Failure(MvpErrorCode errorCode, String message) {
    }

    private record PartialSnapshot(String json, Integer payloadVersion) {
    }
}
