package com.jd.genie.platform.conversation.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.Instant;

@Data
@TableName("conversation")
public class ConversationEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String tenantId;
    private String ownerId;
    private String title;
    private Boolean privacyMode;
    /** Opaque browser-workspace id (see the UI's platform/workspace catalog); null for ordinary chat. */
    private String workspaceId;
    private Long nextTurnNo;
    private Instant lastMessageAt;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;
    private Long version;
}