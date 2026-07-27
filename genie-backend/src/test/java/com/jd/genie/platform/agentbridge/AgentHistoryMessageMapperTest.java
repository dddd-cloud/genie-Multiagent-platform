package com.jd.genie.platform.agentbridge;

import com.jd.genie.agent.dto.Message;
import com.jd.genie.agent.enums.RoleType;
import com.jd.genie.model.req.AgentRequest;
import com.jd.genie.platform.contract.ConversationHistoryItem;
import com.jd.genie.platform.contract.ConversationMessageRole;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AgentHistoryMessageMapperTest {

    private final AgentHistoryMessageMapper mapper = new AgentHistoryMessageMapper();

    @Test
    void mapsFrozenHistoryRolesWithoutTransportOrToolFields() {
        List<ConversationHistoryItem> history = List.of(
                new ConversationHistoryItem(1L, ConversationMessageRole.USER, "上一轮问题"),
                new ConversationHistoryItem(1L, ConversationMessageRole.ASSISTANT, "上一轮回答")
        );

        List<AgentRequest.Message> mapped = mapper.toAgentRequestMessages(history);

        assertEquals(List.of("user", "assistant"), mapped.stream().map(AgentRequest.Message::getRole).toList());
        assertEquals(List.of("上一轮问题", "上一轮回答"), mapped.stream().map(AgentRequest.Message::getContent).toList());
        mapped.forEach(message -> {
            assertNull(message.getCommandCode());
            assertNull(message.getUploadFile());
            assertNull(message.getFiles());
        });
    }

    @Test
    void excludesCurrentQueryAndInvalidBodies() {
        List<ConversationHistoryItem> history = List.of(
                new ConversationHistoryItem(1L, ConversationMessageRole.USER, "保留的问题"),
                new ConversationHistoryItem(1L, ConversationMessageRole.ASSISTANT, "保留的回答"),
                new ConversationHistoryItem(2L, ConversationMessageRole.USER, "当前问题"),
                new ConversationHistoryItem(2L, ConversationMessageRole.ASSISTANT, "   ")
        );

        List<AgentRequest.Message> mapped = mapper.toAgentRequestMessages(history, "当前问题");

        assertEquals(2, mapped.size());
        assertEquals("保留的问题", mapped.get(0).getContent());
        assertEquals("保留的回答", mapped.get(1).getContent());
    }

    @Test
    void mapsOnlyUserAndAssistantRequestMessagesIntoAgentMemory() {
        List<AgentRequest.Message> transportHistory = List.of(
                requestMessage("user", "问题"),
                requestMessage("system", "不得进入"),
                requestMessage("assistant", "回答"),
                requestMessage("tool", "不得进入")
        );

        List<Message> mapped = mapper.toMemoryMessages(transportHistory);

        assertEquals(2, mapped.size());
        assertEquals(RoleType.USER, mapped.get(0).getRole());
        assertEquals("问题", mapped.get(0).getContent());
        assertEquals(RoleType.ASSISTANT, mapped.get(1).getRole());
        assertEquals("回答", mapped.get(1).getContent());
    }

    @Test
    void skipsNullEntriesRolesAndBodies() {
        List<ConversationHistoryItem> frozenHistory = new ArrayList<>();
        frozenHistory.add(null);
        frozenHistory.add(new ConversationHistoryItem(1L, null, "无角色"));
        frozenHistory.add(new ConversationHistoryItem(1L, ConversationMessageRole.USER, null));
        frozenHistory.add(new ConversationHistoryItem(1L, ConversationMessageRole.USER, "保留"));

        List<AgentRequest.Message> transportHistory = new ArrayList<>();
        transportHistory.add(null);
        transportHistory.add(requestMessage(null, "无角色"));
        transportHistory.add(requestMessage("user", null));
        transportHistory.add(requestMessage("assistant", "保留"));

        assertEquals(List.of("保留"), mapper.toAgentRequestMessages(frozenHistory).stream()
                .map(AgentRequest.Message::getContent)
                .toList());
        assertEquals(List.of("保留"), mapper.toMemoryMessages(transportHistory).stream()
                .map(Message::getContent)
                .toList());
    }

    @Test
    void nullOrEmptyHistoryProducesEmptyMessages() {
        assertEquals(List.of(), mapper.toAgentRequestMessages(null));
        assertEquals(List.of(), mapper.toAgentRequestMessages(List.of()));
        assertEquals(List.of(), mapper.toMemoryMessages(null));
    }

    private AgentRequest.Message requestMessage(String role, String content) {
        return AgentRequest.Message.builder().role(role).content(content).build();
    }
}
