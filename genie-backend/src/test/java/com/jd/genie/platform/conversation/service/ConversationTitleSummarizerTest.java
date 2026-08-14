package com.jd.genie.platform.conversation.service;

import com.jd.genie.platform.conversation.mapper.ConversationMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationTitleSummarizerTest {

    @Test
    void sanitizeModelTitleStripsPunctuationAndClampsToNineCharacters() {
        assertEquals("东南亚车市", ConversationTitleService.sanitizeModelTitle("  「东南亚车市」  "));
        assertEquals("车市", ConversationTitleService.sanitizeModelTitle("车市"));
        assertEquals("国产新能源汽车在东", ConversationTitleService.sanitizeModelTitle("国产新能源汽车在东南亚的机会"));
        assertEquals("", ConversationTitleService.sanitizeModelTitle("   "));
        assertEquals("", ConversationTitleService.sanitizeModelTitle(null));
    }

    @Test
    void firstTurnUsesModelSummaryWhenPortReturnsATitle() {
        ConversationMapper mapper = mock(ConversationMapper.class);
        ConversationTitleModelPort port = query -> "东南亚车市";
        when(mapper.autoTitleFirstTurnIfDefault(any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
        ConversationTitleService service = new ConversationTitleService(mapper, port, Runnable::run);
        String query = "请独立搜集并分析国产新能源汽车在东南亚的市场规模";
        String fallback = new ConversationTitleService(mapper).generateTitle(query);

        service.autoTitleFirstTurn(
                "tenant", "owner", "conv-1", 1L,
                query,
                Instant.parse("2026-08-14T00:00:00Z")
        );

        verify(mapper).autoTitleFirstTurnIfDefault(
                eq("tenant"), eq("owner"), eq("conv-1"), eq(2L),
                eq(ConversationTitleService.DEFAULT_TITLE),
                eq(fallback),
                any()
        );
        verify(mapper).autoTitleFirstTurnIfDefault(
                eq("tenant"), eq("owner"), eq("conv-1"), eq(2L),
                eq(fallback),
                eq("东南亚车市"),
                any()
        );
        assertEquals(9, fallback.codePointCount(0, fallback.length()));
    }

    @Test
    void secondTurnDoesNotCallTheModel() {
        ConversationMapper mapper = mock(ConversationMapper.class);
        ConversationTitleModelPort port = query -> "不应出现";
        ConversationTitleService service = new ConversationTitleService(mapper, port, Runnable::run);

        service.autoTitleFirstTurn("tenant", "owner", "conv-1", 2L, "第二问", Instant.now());

        verify(mapper, times(0)).autoTitleFirstTurnIfDefault(any(), any(), any(), any(), any(), any(), any());
    }
}
