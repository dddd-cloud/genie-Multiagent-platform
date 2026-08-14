package com.jd.genie.platform.phase2.runtime.agent;

import com.jd.genie.agent.enums.AgentType;
import com.jd.genie.agent.printer.Printer;
import com.jd.genie.model.response.GptProcessResult;
import com.jd.genie.platform.agentbridge.ConversationStreamObserver;
import com.jd.genie.platform.phase2.runtime.trace.OrchestrationTraceChannel;
import com.jd.genie.platform.phase2contract.BrowserSkillExecutionContract;
import com.jd.genie.platform.phase2contract.dto.BrowserSkillExecutionSignal;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Printer for configured orchestration agents.
 * Optionally forwards safe progress (tool_thought) onto the live trace channel.
 * Transparently forwards frozen browser skill execution signals as skill_execution control packets.
 */
public final class ConfiguredAgentPrinter implements Printer {
    private static final Set<String> SAFE_THOUGHT_TYPES = Set.of("tool_thought");

    private final AtomicInteger progressCount = new AtomicInteger();
    private final OrchestrationTraceChannel traceChannel;
    private final ConversationStreamObserver observer;
    private final Integer attemptNo;
    private final String stepId;
    private final String agentId;
    private final String agentName;

    public ConfiguredAgentPrinter() {
        this(null, null, null, null, null, null);
    }

    public ConfiguredAgentPrinter(
            OrchestrationTraceChannel traceChannel,
            ConversationStreamObserver observer,
            Integer attemptNo,
            String stepId,
            String agentId,
            String agentName
    ) {
        this.traceChannel = traceChannel;
        this.observer = observer;
        this.attemptNo = attemptNo;
        this.stepId = stepId;
        this.agentId = agentId;
        this.agentName = agentName;
    }

    public int progressCount() {
        return progressCount.get();
    }

    @Override
    public void send(String messageId, String messageType, Object message, String digitalEmployee, Boolean isFinal) {
        progressCount.incrementAndGet();
        if (!forwardBrowserSkillSignal(messageType, message)) {
            forwardThought(messageType, message);
        }
    }

    @Override
    public void send(String messageType, Object message) {
        progressCount.incrementAndGet();
        if (!forwardBrowserSkillSignal(messageType, message)) {
            forwardThought(messageType, message);
        }
    }

    @Override
    public void send(String messageType, Object message, String digitalEmployee) {
        progressCount.incrementAndGet();
        if (!forwardBrowserSkillSignal(messageType, message)) {
            forwardThought(messageType, message);
        }
    }

    @Override
    public void send(String messageId, String messageType, Object message, Boolean isFinal) {
        progressCount.incrementAndGet();
        if (!forwardBrowserSkillSignal(messageType, message)) {
            forwardThought(messageType, message);
        }
    }

    @Override
    public void close() {
    }

    @Override
    public void updateAgentType(AgentType agentType) {
    }

    private boolean forwardBrowserSkillSignal(String messageType, Object message) {
        if (observer == null
                || !BrowserSkillExecutionContract.PRINTER_MESSAGE_TYPE.equals(messageType)
                || !(message instanceof BrowserSkillExecutionSignal signal)) {
            return false;
        }
        GptProcessResult packet = GptProcessResult.builder()
                .status("running")
                .response("")
                .responseAll("")
                .finished(false)
                .packageType(BrowserSkillExecutionContract.SSE_PACKAGE_TYPE)
                .resultMap(Map.of(BrowserSkillExecutionContract.RESULT_MAP_KEY, signal))
                .build();
        observer.onEvent(packet);
        return true;
    }

    private void forwardThought(String messageType, Object message) {
        if (traceChannel == null || messageType == null || !SAFE_THOUGHT_TYPES.contains(messageType)) {
            return;
        }
        String text = extractSafeText(message);
        if (text == null || text.isBlank()) {
            return;
        }
        // ReactImplAgent forwards the final model content as tool_thought when not streaming.
        // That content is usually the frozen result JSON — never show it as "thinking".
        if (looksLikeResultContract(text)) {
            return;
        }
        traceChannel.emitStep(attemptNo, stepId, agentId, agentName,
                OrchestrationTraceChannel.KIND_THOUGHT, text, true);
    }

    static boolean looksLikeResultContract(String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            int nl = trimmed.indexOf('\n');
            if (nl > 0) {
                trimmed = trimmed.substring(nl + 1).trim();
            }
        }
        if (!trimmed.startsWith("{")) {
            return false;
        }
        return trimmed.contains("\"status\"")
                && (trimmed.contains("\"SUCCESS\"") || trimmed.contains("\"FAILURE\""))
                && trimmed.contains("\"output\"")
                && trimmed.contains("\"errorCode\"")
                && trimmed.contains("\"retryable\"");
    }

    private static String extractSafeText(Object message) {
        if (message == null) {
            return null;
        }
        if (message instanceof CharSequence sequence) {
            return sequence.toString();
        }
        if (message instanceof Map<?, ?> map) {
            Object content = map.get("content");
            if (content instanceof CharSequence sequence) {
                return sequence.toString();
            }
            Object text = map.get("text");
            if (text instanceof CharSequence sequence) {
                return sequence.toString();
            }
            return null;
        }
        return message.toString();
    }
}
