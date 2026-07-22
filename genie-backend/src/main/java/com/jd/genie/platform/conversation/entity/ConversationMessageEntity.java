package com.jd.genie.platform.conversation.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.Instant;

@Data
@TableName("conversation_message")
public class ConversationMessageEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String conversationId;
    private Long turnNo;
    private String role;
    private String status;
    private String requestId;
    private String content;
    private String streamSnapshot;
    private Integer payloadVersion;
    private Integer deepThink;
    private String outputStyle;
    private String errorCode;
    private String errorMessage;
    private Instant createdAt;
    private Instant updatedAt;
    private Long version;
}