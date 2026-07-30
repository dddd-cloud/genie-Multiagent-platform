package com.jd.genie.platform.agentbridge;

import com.jd.genie.agent.enums.AgentType;
import com.jd.genie.config.GenieConfig;
import com.jd.genie.handler.AgentResponseHandler;
import com.jd.genie.model.req.GptQueryReq;
import com.jd.genie.model.response.GptProcessResult;
import com.jd.genie.platform.contract.support.FakeConversationExecutionPort;
import com.jd.genie.service.impl.MultiAgentServiceImpl;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;
import okio.Okio;
import okio.Source;
import okio.Timeout;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

final class MultiAgentServiceTestSupport {
    static final String INTERNAL_TOKEN = "test-internal-token";
    static final String DEFAULT_AUTO_AGENT_URL = "http://127.0.0.1:8080/AutoAgent";
    static final MediaType EVENT_STREAM = MediaType.parse("text/event-stream");

    private MultiAgentServiceTestSupport() {
    }

    static Scenario scenario(Script script, AgentResponseHandler handler) {
        return scenario(
                script,
                handler,
                INTERNAL_TOKEN,
                DEFAULT_AUTO_AGENT_URL,
                SnapshotPruner.DEFAULT_MAX_BYTES
        );
    }

    static Scenario scenario(
            Script script,
            AgentResponseHandler handler,
            String internalToken,
            long maxSnapshotBytes
    ) {
        return scenario(script, handler, internalToken, DEFAULT_AUTO_AGENT_URL, maxSnapshotBytes);
    }

    static Scenario scenario(
            Script script,
            AgentResponseHandler handler,
            String internalToken,
            String autoAgentUrl,
            long maxSnapshotBytes
    ) {
        ScriptedCallFactory calls = new ScriptedCallFactory(script);
        FakeConversationExecutionPort port = new FakeConversationExecutionPort();
        ObserverTestSupport.RecordingClientChannel channel =
                new ObserverTestSupport.RecordingClientChannel();
        CancellableAgentCall cancellableCall = new CancellableAgentCall();
        ConversationStreamObserver observer = ObserverTestSupport.observer(
                port,
                channel,
                maxSnapshotBytes,
                cancellableCall
        );
        MultiAgentServiceImpl service = new MultiAgentServiceImpl(
                new GenieConfig(),
                handler == null ? Map.of() : Map.of(AgentType.REACT, handler),
                calls,
                internalToken,
                autoAgentUrl
        );
        return new Scenario(service, calls, port, channel, observer, cancellableCall);
    }

    static AgentResponseHandler returning(GptProcessResult result) {
        return (request, response, responses, eventResult) -> result;
    }

    static Script respond(int status, ResponseBody body) {
        return (call, callback) -> callback.onResponse(
                call,
                response(call.request(), status, body)
        );
    }

    static Response response(Request request, int status, ResponseBody body) {
        return new Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(status)
                .message("scripted")
                .body(body)
                .build();
    }

    static Script connectionFailure() {
        return (call, callback) -> callback.onFailure(
                call,
                new IOException("scripted connection failure")
        );
    }

    static Script pending() {
        return (call, callback) -> {
        };
    }

    static ResponseBody stream(String data) {
        return ResponseBody.create(EVENT_STREAM, data);
    }

    static TrackingResponseBody trackedStream(String data) {
        return new TrackingResponseBody(data);
    }

    static ResponseBody interruptedStream(String firstEvent) {
        byte[] prefix = firstEvent.getBytes(StandardCharsets.UTF_8);
        return new ResponseBody() {
            @Override
            public MediaType contentType() {
                return EVENT_STREAM;
            }

            @Override
            public long contentLength() {
                return -1L;
            }

            @Override
            public BufferedSource source() {
                return Okio.buffer(new Source() {
                    private boolean emitted;

                    @Override
                    public long read(Buffer sink, long byteCount) throws IOException {
                        if (!emitted) {
                            emitted = true;
                            sink.write(prefix);
                            return prefix.length;
                        }
                        throw new IOException("scripted stream interruption");
                    }

                    @Override
                    public Timeout timeout() {
                        return Timeout.NONE;
                    }

                    @Override
                    public void close() {
                    }
                });
            }
        };
    }

    static GptQueryReq request() {
        return GptQueryReq.builder()
                .sessionId("123e4567-e89b-12d3-a456-426614174000")
                .requestId("request-1")
                .traceId("trace-1")
                .user("alice")
                .query("question")
                .deepThink(0)
                .outputStyle("docs")
                .build();
    }

    record Scenario(
            MultiAgentServiceImpl service,
            ScriptedCallFactory calls,
            FakeConversationExecutionPort port,
            ObserverTestSupport.RecordingClientChannel channel,
            ConversationStreamObserver observer,
            CancellableAgentCall cancellableCall
    ) {
        void start() {
            if (!observer.markStreaming()) {
                throw new IllegalStateException("Observer could not enter STREAMING");
            }
            service.searchForAgentRequest(request(), observer, cancellableCall);
        }
    }

    @FunctionalInterface
    interface Script {
        void run(Call call, Callback callback) throws IOException;
    }

    static final class TrackingResponseBody extends ResponseBody {
        private final AtomicBoolean closed = new AtomicBoolean();
        private final BufferedSource source;

        private TrackingResponseBody(String data) {
            Buffer content = new Buffer().writeUtf8(data);
            source = Okio.buffer(new Source() {
                @Override
                public long read(Buffer sink, long byteCount) {
                    return content.read(sink, byteCount);
                }

                @Override
                public Timeout timeout() {
                    return Timeout.NONE;
                }

                @Override
                public void close() {
                    closed.set(true);
                }
            });
        }

        @Override
        public MediaType contentType() {
            return EVENT_STREAM;
        }

        @Override
        public long contentLength() {
            return -1L;
        }

        @Override
        public BufferedSource source() {
            return source;
        }

        boolean isClosed() {
            return closed.get();
        }
    }

    static final class ScriptedCallFactory implements Call.Factory {
        private final Script script;
        private ScriptedCall lastCall;

        private ScriptedCallFactory(Script script) {
            this.script = script;
        }

        @Override
        public Call newCall(Request request) {
            lastCall = new ScriptedCall(request, script);
            return lastCall;
        }

        ScriptedCall lastCall() {
            return lastCall;
        }
    }

    static final class ScriptedCall implements Call {
        private final Request request;
        private final Script script;
        private final AtomicInteger cancellationCount = new AtomicInteger();
        private boolean executed;
        private boolean canceled;
        private Callback callback;

        private ScriptedCall(Request request, Script script) {
            this.request = request;
            this.script = script;
        }

        @Override
        public Request request() {
            return request;
        }

        @Override
        public Response execute() {
            throw new UnsupportedOperationException("Only asynchronous calls are supported");
        }

        @Override
        public void enqueue(Callback callback) {
            executed = true;
            this.callback = callback;
            try {
                script.run(this, callback);
            } catch (IOException error) {
                callback.onFailure(this, error);
            }
        }

        @Override
        public void cancel() {
            canceled = true;
            cancellationCount.incrementAndGet();
        }

        @Override
        public boolean isExecuted() {
            return executed;
        }

        @Override
        public boolean isCanceled() {
            return canceled;
        }

        @Override
        public Timeout timeout() {
            return Timeout.NONE;
        }

        @Override
        public Call clone() {
            return new ScriptedCall(request, script);
        }

        int cancellationCount() {
            return cancellationCount.get();
        }

        void signalFailure(IOException error) {
            if (callback == null) {
                throw new IllegalStateException("Call has not been enqueued");
            }
            callback.onFailure(this, error);
        }

        void signalResponse(int status, ResponseBody body) throws IOException {
            if (callback == null) {
                throw new IllegalStateException("Call has not been enqueued");
            }
            callback.onResponse(this, response(request, status, body));
        }
    }
}
