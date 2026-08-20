package com.jd.genie.platform.conversation.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.Instant;

@Data
@TableName("conversation_attachment")
public class ConversationAttachmentEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String conversationId;
    private String tenantId;
    private String ownerId;
    private String fileName;
    private String fileType;
    private String mimeType;
    private Long sizeBytes;
    private String extractedText;
    private Boolean truncated;
    private Instant createdAt;
}
