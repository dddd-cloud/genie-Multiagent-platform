package com.jd.genie.platform.phase2.runtime.agent;

import com.jd.genie.agent.enums.AgentType;
import com.jd.genie.agent.printer.Printer;
import com.jd.genie.platform.phase2.runtime.trace.OrchestrationTraceChannel;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Printer for configured orchestration agents.
 * Optionally forwards safe progress (tool_thought) onto the live trace channel.
 */
public final class ConfiguredAgentPrinter implements Printer {
    private static final Set<String> SAFE_THOUGHT_TYPES = Set.of("tool_thought");

    private final AtomicInteger progressCount = new AtomicInteger();
    private final OrchestrationTraceChannel traceChannel;
    private final Integer attemptNo;
    private final String stepId;
    private final String agentId;
    private final String agentName;

    public ConfiguredAgentPrinter() {
        this(null, null, null, null, null);
    }

    public ConfiguredAgentPrinter(
            OrchestrationTraceChannel traceChannel,
            Integer attemptNo,
            String stepId,
            String agentId,
            String agentName
    ) {
        this.traceChannel = traceChannel;
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
        forwardThought(messageType, message);
    }

    @Override
    public void send(String messageType, Object message) {
        progressCount.incrementAndGet();
        forwardThought(messageType, message);
    }

    @Override
    public void send(String messageType, Object message, String digitalEmployee) {
        progressCount.incrementAndGet();
        forwardThought(messageType, message);
    }

    @Override
    public void send(String messageId, String messageType, Object message, Boolean isFinal) {
        progressCount.incrementAndGet();
        forwardThought(messageType, message);
    }

    @Override
    public void close() {
    }

    @Override
    public void updateAgentType(AgentType agentType) {
    }

    private void forwardThought(String messageType, Object message) {
        if (traceChannel == null || messageType == null || !SAFE_THOUGHT_TYPES.contains(messageType)) {
            return;
        }
        String text = extractSafeText(message);
        if (text == null || text.isBlank()) {
            return;
        }
        traceChannel.emitStep(attemptNo, stepId, agentId, agentName,
                OrchestrationTraceChannel.KIND_THOUGHT, text, true);
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
