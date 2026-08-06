package com.jd.genie.platform.phase2.runtime.agent;

import com.jd.genie.agent.enums.AgentType;
import com.jd.genie.agent.printer.Printer;

import java.util.concurrent.atomic.AtomicInteger;

public final class ConfiguredAgentPrinter implements Printer {
    private final AtomicInteger progressCount = new AtomicInteger();

    public int progressCount() {
        return progressCount.get();
    }

    @Override
    public void send(String messageId, String messageType, Object message, String digitalEmployee, Boolean isFinal) {
        progressCount.incrementAndGet();
    }

    @Override
    public void send(String messageType, Object message) {
        progressCount.incrementAndGet();
    }

    @Override
    public void send(String messageType, Object message, String digitalEmployee) {
        progressCount.incrementAndGet();
    }

    @Override
    public void send(String messageId, String messageType, Object message, Boolean isFinal) {
        progressCount.incrementAndGet();
    }

    @Override
    public void close() {
    }

    @Override
    public void updateAgentType(AgentType agentType) {
    }
}
