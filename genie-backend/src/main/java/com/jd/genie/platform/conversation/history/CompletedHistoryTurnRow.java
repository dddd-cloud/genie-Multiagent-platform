package com.jd.genie.platform.conversation.history;

import lombok.Data;

@Data
public class CompletedHistoryTurnRow {
    private Long turnNo;
    private String requestId;
    private String userContent;
    private String assistantContent;
}