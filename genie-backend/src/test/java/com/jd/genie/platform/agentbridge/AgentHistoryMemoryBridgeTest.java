package com.jd.genie.platform.agentbridge;

import com.jd.genie.agent.dto.Memory;
import com.jd.genie.agent.dto.Message;
import com.jd.genie.agent.enums.RoleType;
import com.jd.genie.model.req.AgentRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentHistoryMemoryBridgeTest {
    private final AgentHistoryMemoryBridge bridge = new AgentHistoryMemoryBridge();

    @Test
    void appendsOnlyOrderedUserAndAssistantHistoryToTargetMemory() {
        Memory target = new Memory();

        bridge.appendTo(target, List.of(
                message("user", "第一轮问题"),
                message("assistant", "第一轮回答"),
                message("system", "不得注入"),
                message("tool", "不得注入")
        ));

        assertEquals(List.of(RoleType.USER, RoleType.ASSISTANT), target.getMessages().stream()
                .map(Message::getRole)
                .toList());
        assertEquals(List.of("第一轮问题", "第一轮回答"), target.getMessages().stream()
                .map(Message::getContent)
                .toList());
    }

    @Test
    void createsIndependentHistoryForReactAndPlanningTargets() {
        List<AgentRequest.Message> history = List.of(
                message("user", "已知事实"),
                message("assistant", "已知结论")
        );
        Memory reactMemory = new Memory();
        Memory planningMemory = new Memory();

        bridge.appendTo(reactMemory, history);
        bridge.appendTo(planningMemory, history);

        assertEquals(reactMemory.getMessages(), planningMemory.getMessages());
        assertNotSame(reactMemory.getMessages().get(0), planningMemory.getMessages().get(0));
        assertTrue(reactMemory.getMessages().stream().noneMatch(
                message -> message.getRole() == RoleType.TOOL
        ));
        assertTrue(planningMemory.getMessages().stream().noneMatch(
                message -> message.getRole() == RoleType.TOOL
        ));
    }

    private AgentRequest.Message message(String role, String content) {
        return AgentRequest.Message.builder().role(role).content(content).build();
    }
}
