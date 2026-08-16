package com.jd.genie.platform.phase2.memory.capture;

import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.UserRole;
import com.jd.genie.platform.conversation.entity.ConversationEntity;
import com.jd.genie.platform.conversation.entity.ConversationMessageEntity;
import com.jd.genie.platform.conversation.mapper.ConversationMapper;
import com.jd.genie.platform.conversation.mapper.ConversationMessageMapper;
import com.jd.genie.platform.phase2.configuration.memory.dto.ConversationSummaryAnalysisRequest;
import com.jd.genie.platform.phase2.configuration.memory.dto.ConversationSummaryResponse;
import com.jd.genie.platform.phase2.configuration.memory.dto.MemoryAnalysisRequest;
import com.jd.genie.platform.phase2.configuration.memory.dto.MemoryPatchItem;
import com.jd.genie.platform.phase2.configuration.memory.dto.MemoryPatchResponse;
import com.jd.genie.platform.phase2.configuration.memory.service.ConversationSummaryAnalysisService;
import com.jd.genie.platform.phase2.configuration.memory.service.MemoryAnalysisService;
import com.jd.genie.platform.phase2.memory.store.MemoryDiskStore;
import com.jd.genie.platform.phase2.memory.store.MemoryDocumentService;
import com.jd.genie.platform.phase2.memory.store.MemoryFileSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemoryTurnCaptureServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void captureWritesLongTermMemoryForCompletedTurn() {
        ConversationMapper conversations = mock(ConversationMapper.class);
        ConversationMessageMapper messages = mock(ConversationMessageMapper.class);
        MemoryAnalysisService analysis = mock(MemoryAnalysisService.class);
        ConversationSummaryAnalysisService summary = mock(ConversationSummaryAnalysisService.class);
        MemoryDocumentService documents = new MemoryDocumentService(new MemoryDiskStore(tempDir.toString()));
        MemoryTurnCaptureService service = new MemoryTurnCaptureService(
            conversations, messages, analysis, summary, documents, Runnable::run);

        ConversationEntity conversation = conversation("conv-1", false);
        when(conversations.selectOwnedConversation("t", "u", "conv-1")).thenReturn(conversation);
        when(messages.selectOwnedMessage("t", "u", "a1")).thenReturn(assistant("a1", "conv-1", "req-1", 1L, "hello"));
        when(messages.selectOwnedMessageByRequestRole("t", "u", "conv-1", "req-1", "USER"))
            .thenReturn(user("u1", "conv-1", "req-1", 1L, "我叫李四"));
        when(messages.selectMessagesByOwnedConversation("t", "u", "conv-1")).thenReturn(List.of(
            user("u1", "conv-1", "req-1", 1L, "我叫李四"),
            assistant("a1", "conv-1", "req-1", 1L, "hello")
        ));
        when(analysis.analyzeTurn(any(MemoryAnalysisRequest.class))).thenReturn(
            new MemoryPatchResponse(1, List.of(new MemoryPatchItem("UPSERT", "基本信息", "姓名", "李四")))
        );
        when(summary.summarize(any(ConversationSummaryAnalysisRequest.class))).thenReturn(
            new ConversationSummaryResponse(1, """
                ## 当前目标
                - 自我介绍

                ## 已确认事实
                - 李四

                ## 已完成内容
                - 暂无

                ## 未解决事项
                - 暂无
                """)
        );

        service.capture(user(), "a1");

        MemoryFileSnapshot ltm = documents.readLongTerm("u");
        assertEquals(MemoryFileSnapshot.Status.READY, ltm.status());
        assertFalse(ltm.markdown() == null || !ltm.markdown().contains("李四"));
        verify(analysis).analyzeTurn(any(MemoryAnalysisRequest.class));
    }

    @Test
    void privacyModeSkipsCapture() {
        ConversationMapper conversations = mock(ConversationMapper.class);
        ConversationMessageMapper messages = mock(ConversationMessageMapper.class);
        MemoryAnalysisService analysis = mock(MemoryAnalysisService.class);
        ConversationSummaryAnalysisService summary = mock(ConversationSummaryAnalysisService.class);
        MemoryDocumentService documents = new MemoryDocumentService(new MemoryDiskStore(tempDir.toString()));
        MemoryTurnCaptureService service = new MemoryTurnCaptureService(
            conversations, messages, analysis, summary, documents, Runnable::run);

        when(messages.selectOwnedMessage("t", "u", "a1")).thenReturn(assistant("a1", "conv-p", "req-1", 1L, "hello"));
        when(conversations.selectOwnedConversation("t", "u", "conv-p")).thenReturn(conversation("conv-p", true));

        service.capture(user(), "a1");

        verify(analysis, never()).analyzeTurn(any());
        verify(summary, never()).summarize(any());
        assertEquals(MemoryFileSnapshot.Status.EMPTY, documents.readLongTerm("u").status());
    }

    private static CurrentUser user() {
        return new CurrentUser("t", "u", "u", "u", UserRole.USER);
    }

    private static ConversationEntity conversation(String id, boolean privacy) {
        ConversationEntity entity = new ConversationEntity();
        entity.setId(id);
        entity.setTenantId("t");
        entity.setOwnerId("u");
        entity.setTitle("新对话");
        entity.setPrivacyMode(privacy);
        entity.setNextTurnNo(2L);
        entity.setCreatedAt(Instant.parse("2026-08-16T00:00:00Z"));
        entity.setUpdatedAt(entity.getCreatedAt());
        return entity;
    }

    private static ConversationMessageEntity user(
        String id, String conversationId, String requestId, long turnNo, String content
    ) {
        return message(id, conversationId, requestId, turnNo, "USER", content);
    }

    private static ConversationMessageEntity assistant(
        String id, String conversationId, String requestId, long turnNo, String content
    ) {
        return message(id, conversationId, requestId, turnNo, "ASSISTANT", content);
    }

    private static ConversationMessageEntity message(
        String id, String conversationId, String requestId, long turnNo, String role, String content
    ) {
        ConversationMessageEntity entity = new ConversationMessageEntity();
        entity.setId(id);
        entity.setConversationId(conversationId);
        entity.setRequestId(requestId);
        entity.setTurnNo(turnNo);
        entity.setRole(role);
        entity.setStatus("COMPLETED");
        entity.setContent(content);
        entity.setCreatedAt(Instant.parse("2026-08-16T00:00:00Z"));
        entity.setUpdatedAt(entity.getCreatedAt());
        return entity;
    }
}
