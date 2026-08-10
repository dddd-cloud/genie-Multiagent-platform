package com.jd.genie.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.jd.genie.agent.enums.AgentType;
import com.jd.genie.agent.enums.ResponseTypeEnum;
import com.jd.genie.config.GenieConfig;
import com.jd.genie.handler.AgentResponseHandler;
import com.jd.genie.model.multi.EventResult;
import com.jd.genie.model.req.AgentRequest;
import com.jd.genie.model.req.GptQueryReq;
import com.jd.genie.model.response.AgentResponse;
import com.jd.genie.model.response.GptProcessResult;
import com.jd.genie.platform.agentbridge.AgentBridgeException;
import com.jd.genie.platform.agentbridge.CancellableAgentCall;
import com.jd.genie.platform.agentbridge.ConversationStreamObserver;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.service.IMultiAgentService;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class MultiAgentServiceImpl implements IMultiAgentService {
    static final String INTERNAL_TOKEN_HEADER = "X-Genie-Internal-Token";
    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json");

    private final GenieConfig genieConfig;
    private final Map<AgentType, AgentResponseHandler> handlerMap;
    private final Call.Factory callFactory;
    private final String internalAgentToken;
    private final String autoAgentUrl;

    @Autowired
    public MultiAgentServiceImpl(
            GenieConfig genieConfig,
            Map<AgentType, AgentResponseHandler> handlerMap,
            @Value("${GENIE_INTERNAL_AGENT_TOKEN:}") String internalAgentToken,
            @Value("${genie.agent-bridge.auto-agent-url}") String autoAgentUrl,
            @Value("${genie.agent-bridge.connect-timeout-millis}") long connectTimeoutMillis,
            @Value("${genie.agent-bridge.read-timeout-millis}") long readTimeoutMillis,
            @Value("${genie.agent-bridge.call-timeout-millis}") long callTimeoutMillis
    ) {
        this(
                genieConfig,
                handlerMap,
                buildHttpClient(connectTimeoutMillis, readTimeoutMillis, callTimeoutMillis),
                internalAgentToken,
                autoAgentUrl
        );
    }

    public MultiAgentServiceImpl(
            GenieConfig genieConfig,
            Map<AgentType, AgentResponseHandler> handlerMap,
            Call.Factory callFactory,
            String internalAgentToken,
            String autoAgentUrl
    ) {
        this.genieConfig = Objects.requireNonNull(genieConfig, "genieConfig");
        this.handlerMap = Map.copyOf(Objects.requireNonNull(handlerMap, "handlerMap"));
        this.callFactory = Objects.requireNonNull(callFactory, "callFactory");
        this.internalAgentToken = internalAgentToken;
        this.autoAgentUrl = requireText(autoAgentUrl, "autoAgentUrl");
    }

    @Override
    public void searchForAgentRequest(
            GptQueryReq request,
            ConversationStreamObserver observer,
            CancellableAgentCall cancellableCall
    ) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(observer, "observer");
        Objects.requireNonNull(cancellableCall, "cancellableCall");

        AgentRequest agentRequest = buildAgentRequest(request);
        Request httpRequest = buildHttpRequest(agentRequest);
        Call call = callFactory.newCall(httpRequest);
        cancellableCall.bind(call::cancel);
        if (cancellableCall.isCancellationRequested()) {
            return;
        }

        log.info(
                "Internal Agent request started, traceId: {}, agentType: {}",
                agentRequest.getRequestId(),
                agentRequest.getAgentType()
        );
        call.enqueue(new AgentStreamCallback(agentRequest, observer));
    }

    private final class AgentStreamCallback implements Callback {
        private final AgentRequest request;
        private final ConversationStreamObserver observer;
        private final List<AgentResponse> agentResponses = new ArrayList<>();
        private final EventResult eventResult = new EventResult();
        private final long startedAtMillis = System.currentTimeMillis();

        private AgentStreamCallback(
                AgentRequest request,
                ConversationStreamObserver observer
        ) {
            this.request = request;
            this.observer = observer;
        }

        @Override
        public void onFailure(Call call, IOException error) {
            if (observer.isTerminal()) {
                return;
            }
            fail(
                    MvpErrorCode.AGENT_DOWNSTREAM_ERROR,
                    "Internal Agent connection failed",
                    error
            );
        }

        @Override
        public void onResponse(Call call, Response response) {
            try (response) {
                if (observer.isTerminal()) {
                    return;
                }
                if (!response.isSuccessful()) {
                    fail(
                            MvpErrorCode.AGENT_DOWNSTREAM_ERROR,
                            "Internal Agent returned a non-success HTTP status",
                            null
                    );
                    return;
                }

                ResponseBody body = response.body();
                if (body == null) {
                    fail(
                            MvpErrorCode.AGENT_DOWNSTREAM_ERROR,
                            "Internal Agent returned an empty response body",
                            null
                    );
                    return;
                }
                consume(body);
            } catch (IOException error) {
                if (!observer.isTerminal()) {
                    fail(
                            MvpErrorCode.AGENT_STREAM_INTERRUPTED,
                            "Internal Agent stream was interrupted",
                            error
                    );
                }
            } catch (RuntimeException error) {
                if (!observer.isTerminal()) {
                    fail(
                            MvpErrorCode.AGENT_STREAM_INTERRUPTED,
                            "Internal Agent stream event could not be processed",
                            error
                    );
                }
            }
        }

        private void consume(ResponseBody body) throws IOException {
            if (!isEventStream(body.contentType())) {
                fail(
                        MvpErrorCode.AGENT_DOWNSTREAM_ERROR,
                        "Internal Agent returned a non-SSE response",
                        null
                );
                return;
            }

            boolean sawSseEvent = false;
            StringBuilder data = new StringBuilder();
            boolean hasDataField = false;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(body.byteStream(), StandardCharsets.UTF_8)
            )) {
                String line;
                while (!observer.isTerminal() && (line = reader.readLine()) != null) {
                    if (line.isEmpty()) {
                        if (hasDataField) {
                            sawSseEvent = true;
                            if (!dispatch(data.toString())) {
                                return;
                            }
                            data.setLength(0);
                            hasDataField = false;
                        }
                        continue;
                    }
                    if (line.startsWith(":")) {
                        continue;
                    }

                    int delimiter = line.indexOf(':');
                    String field = delimiter < 0 ? line : line.substring(0, delimiter);
                    if (!"data".equals(field)) {
                        continue;
                    }
                    String value = delimiter < 0 ? "" : line.substring(delimiter + 1);
                    if (value.startsWith(" ")) {
                        value = value.substring(1);
                    }
                    if (hasDataField) {
                        data.append('\n');
                    }
                    data.append(value);
                    hasDataField = true;
                }

                if (!observer.isTerminal() && hasDataField) {
                    sawSseEvent = true;
                    if (!dispatch(data.toString())) {
                        return;
                    }
                }
            }
            if (observer.isTerminal()) {
                return;
            }
            if (!sawSseEvent) {
                fail(
                        MvpErrorCode.AGENT_DOWNSTREAM_ERROR,
                        "Internal Agent returned no SSE data",
                        null
                );
                return;
            }
            failNoFinalEvent();
        }

        private boolean dispatch(String data) {
            if ("[DONE]".equals(data)) {
                failNoFinalEvent();
                return false;
            }
            if (data.startsWith("heartbeat")) {
                // Heartbeat must never mark CLIENT_DISCONNECTED — keep the agent run alive.
                observer.onEventBestEffort(buildHeartbeatData(request.getRequestId()));
                return !observer.isTerminal();
            }

            GptProcessResult result = mapBusinessEvent(data);
            if (!observer.onEvent(result) || observer.isTerminal()) {
                return false;
            }
            if (!result.isFinished()) {
                return true;
            }
            if (!"success".equalsIgnoreCase(result.getStatus())) {
                fail(
                        MvpErrorCode.AGENT_DOWNSTREAM_ERROR,
                        "Internal Agent emitted a failed final event",
                        null
                );
                return false;
            }
            observer.onCompleted();
            if (observer.state() == ConversationStreamObserver.TerminalState.COMPLETED) {
                log.info(
                        "Internal Agent request completed, traceId: {}, durationMs: {}",
                        request.getRequestId(),
                        System.currentTimeMillis() - startedAtMillis
                );
            }
            return false;
        }

        private GptProcessResult mapBusinessEvent(String data) {
            AgentResponse response;
            try {
                response = JSON.parseObject(data, AgentResponse.class);
            } catch (RuntimeException error) {
                throw eventMappingFailure("Internal Agent emitted malformed JSON", error);
            }
            if (response == null) {
                throw eventMappingFailure("Internal Agent emitted an empty event", null);
            }

            AgentType agentType;
            try {
                agentType = AgentType.fromCode(request.getAgentType());
            } catch (RuntimeException error) {
                throw eventMappingFailure("Internal Agent type is unsupported", error);
            }
            AgentResponseHandler handler = handlerMap.get(agentType);
            if (handler == null) {
                throw eventMappingFailure("Internal Agent response handler is unavailable", null);
            }

            GptProcessResult result = handler.handle(
                    request,
                    response,
                    agentResponses,
                    eventResult
            );
            if (result == null) {
                throw eventMappingFailure("Internal Agent response handler returned no event", null);
            }
            return result;
        }

        private void failNoFinalEvent() {
            fail(
                    MvpErrorCode.AGENT_NO_FINAL_EVENT,
                    "Internal Agent stream ended without a successful final event",
                    null
            );
        }

        private void fail(MvpErrorCode errorCode, String message, Throwable cause) {
            log.warn(
                    "Internal Agent request failed, traceId: {}, errorCode: {}",
                    request.getRequestId(),
                    errorCode
            );
            observer.onError(new AgentBridgeException(errorCode, message, cause));
        }
    }

    private Request buildHttpRequest(AgentRequest request) {
        if (internalAgentToken == null || internalAgentToken.isBlank()) {
            throw new AgentBridgeException(
                    MvpErrorCode.INTERNAL_ERROR,
                    "GENIE_INTERNAL_AGENT_TOKEN is not configured"
            );
        }
        RequestBody body = RequestBody.create(
                JSON_MEDIA_TYPE,
                JSONObject.toJSONString(request)
        );
        return new Request.Builder()
                .url(autoAgentUrl)
                .header(INTERNAL_TOKEN_HEADER, internalAgentToken)
                .post(body)
                .build();
    }

    private AgentRequest buildAgentRequest(GptQueryReq request) {
        AgentRequest agentRequest = new AgentRequest();
        agentRequest.setRequestId(request.getTraceId());
        agentRequest.setErp(request.getUser());
        agentRequest.setQuery(request.getQuery());
        agentRequest.setMessages(request.getHistoryMessages());
        agentRequest.setAgentType(request.getDeepThink() == 0 ? 5 : 3);
        agentRequest.setSopPrompt(
                agentRequest.getAgentType() == 3
                        ? promptOrDefault(request.getRuntimeSopPrompt(), genieConfig.getGenieSopPrompt())
                        : ""
        );
        agentRequest.setBasePrompt(
                agentRequest.getAgentType() == 5
                        ? promptOrDefault(request.getRuntimeBasePrompt(), genieConfig.getGenieBasePrompt())
                        : ""
        );
        agentRequest.setIsStream(true);
        agentRequest.setOutputStyle(request.getOutputStyle());
        return agentRequest;
    }

    private String promptOrDefault(String runtimePrompt, String configuredPrompt) {
        if (runtimePrompt == null || runtimePrompt.isBlank()) {
            return configuredPrompt;
        }
        if (configuredPrompt == null || configuredPrompt.isBlank()) {
            return runtimePrompt;
        }
        return configuredPrompt + "\n\n" + runtimePrompt;
    }

    private GptProcessResult buildHeartbeatData(String requestId) {
        GptProcessResult result = new GptProcessResult();
        result.setFinished(false);
        result.setStatus("success");
        result.setResponseType(ResponseTypeEnum.text.name());
        result.setResponse("");
        result.setResponseAll("");
        result.setUseTimes(0);
        result.setUseTokens(0);
        result.setReqId(requestId);
        result.setPackageType("heartbeat");
        result.setEncrypted(false);
        return result;
    }

    private boolean isEventStream(MediaType contentType) {
        return contentType != null
                && "text".equalsIgnoreCase(contentType.type())
                && "event-stream".equalsIgnoreCase(contentType.subtype());
    }

    private IllegalStateException eventMappingFailure(String message, Throwable cause) {
        return cause == null
                ? new IllegalStateException(message)
                : new IllegalStateException(message, cause);
    }

    static OkHttpClient buildHttpClient(
            long connectTimeoutMillis,
            long readTimeoutMillis,
            long callTimeoutMillis
    ) {
        return new OkHttpClient.Builder()
                .connectTimeout(requirePositive(connectTimeoutMillis, "connectTimeoutMillis"), TimeUnit.MILLISECONDS)
                .readTimeout(requirePositive(readTimeoutMillis, "readTimeoutMillis"), TimeUnit.MILLISECONDS)
                .writeTimeout(callTimeoutMillis, TimeUnit.MILLISECONDS)
                .callTimeout(requirePositive(callTimeoutMillis, "callTimeoutMillis"), TimeUnit.MILLISECONDS)
                .build();
    }

    private static long requirePositive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
