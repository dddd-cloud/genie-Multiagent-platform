package com.jd.genie.platform.phase2.memory.store;

import com.jd.genie.platform.conversation.entity.ConversationEntity;
import com.jd.genie.platform.conversation.mapper.ConversationMapper;
import com.jd.genie.platform.phase2.configuration.memory.dto.MemoryPatchItem;
import com.jd.genie.platform.phase2.configuration.memory.dto.MemoryPatchResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MemoryDocumentServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void persistAnalyzeResultWritesLongTermFile() {
        MemoryDocumentService service = new MemoryDocumentService(new MemoryDiskStore(tempDir));
        service.persistAnalyzeResult("user-a", new MemoryPatchResponse(1, List.of(
            new MemoryPatchItem("UPSERT", "长期目标", "learnDocker", "学习 Docker")
        )));
        MemoryFileSnapshot snapshot = service.readLongTerm("user-a");
        assertEquals(MemoryFileSnapshot.Status.READY, snapshot.status());
        assertTrue(snapshot.markdown().contains("learnDocker"));
        assertTrue(snapshot.markdown().contains("长期目标"));
    }

    @Test
    void loadForQueryReadsDiskAndIgnoresMissingSummary() {
        MemoryDocumentService service = new MemoryDocumentService(new MemoryDiskStore(tempDir));
        service.persistAnalyzeResult("user-a", new MemoryPatchResponse(1, List.of(
            new MemoryPatchItem("UPSERT", "基本信息", "name", "Ada")
        )));
        LocalMemorySnapshot loaded = service.loadForQuery("user-a", "missing-conversation");
        assertTrue(loaded.longTermMemory().contains("Ada"));
        assertEquals("", loaded.conversationSummary());
    }

    @Test
    void persistSummaryWritesConversationFile() {
        MemoryDocumentService service = new MemoryDocumentService(new MemoryDiskStore(tempDir));
        service.persistSummaryMarkdown(
            "user-a",
            "conv-1",
            "## 当前目标\n- demo\n\n## 已确认事实\n- fact\n\n## 已完成内容\n- done\n\n## 未解决事项\n- none",
            5
        );
        MemoryFileSnapshot snapshot = service.readSummary("user-a", "conv-1");
        assertEquals(MemoryFileSnapshot.Status.READY, snapshot.status());
        assertTrue(snapshot.markdown().contains("lastSummarizedTurnNo: 5"));
        assertEquals(1, service.listSummaries("user-a").size());
        assertEquals("conv-1", service.listSummaries("user-a").get(0).conversationId());
    }

    @Test
    void loadForQuerySkipsPrivacyConversations() {
        ConversationMapper mapper = mock(ConversationMapper.class);
        ConversationEntity conversation = new ConversationEntity();
        conversation.setPrivacyMode(true);
        when(mapper.selectOwnedConversation("tenant-a", "user-a", "conv-private")).thenReturn(conversation);

        MemoryDocumentService service = new MemoryDocumentService(new MemoryDiskStore(tempDir), mapper);
        service.persistAnalyzeResult("user-a", new MemoryPatchResponse(1, List.of(
            new MemoryPatchItem("UPSERT", "基本信息", "name", "Ada")
        )));
        service.persistSummaryMarkdown(
            "user-a",
            "conv-private",
            "## 当前目标\n- demo\n\n## 已确认事实\n- fact\n\n## 已完成内容\n- done\n\n## 未解决事项\n- none",
            1
        );

        LocalMemorySnapshot loaded = service.loadForQuery("tenant-a", "user-a", "conv-private");
        assertEquals("", loaded.longTermMemory());
        assertEquals("", loaded.conversationSummary());
    }
}
