package com.jd.genie.platform.conversation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jd.genie.platform.conversation.entity.ConversationMessageEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.List;

@Mapper
public interface ConversationMessageMapper extends BaseMapper<ConversationMessageEntity> {

    @Select("""
        SELECT COUNT(1) > 0
        FROM conversation_message m
        JOIN conversation c ON c.id = m.conversation_id
        WHERE c.tenant_id = #{tenantId}
          AND c.owner_id = #{ownerId}
          AND c.deleted_at IS NULL
          AND m.conversation_id = #{conversationId}
          AND m.request_id = #{requestId}
        """)
    boolean existsRequestId(@Param("tenantId") String tenantId,
                            @Param("ownerId") String ownerId,
                            @Param("conversationId") String conversationId,
                            @Param("requestId") String requestId);

    @Select("""
        SELECT COUNT(1) > 0
        FROM conversation_message m
        JOIN conversation c ON c.id = m.conversation_id
        WHERE c.tenant_id = #{tenantId}
          AND c.owner_id = #{ownerId}
          AND c.deleted_at IS NULL
          AND m.conversation_id = #{conversationId}
          AND m.role = 'ASSISTANT'
          AND m.status IN ('PENDING', 'STREAMING')
        """)
    boolean existsActiveAssistant(@Param("tenantId") String tenantId,
                                  @Param("ownerId") String ownerId,
                                  @Param("conversationId") String conversationId);

    @Select("""
        SELECT m.*
        FROM conversation_message m
        JOIN conversation c ON c.id = m.conversation_id
        WHERE c.tenant_id = #{tenantId}
          AND c.owner_id = #{ownerId}
          AND c.deleted_at IS NULL
          AND m.conversation_id = #{conversationId}
        ORDER BY m.turn_no ASC, m.created_at ASC, m.id ASC
        """)
    List<ConversationMessageEntity> selectMessagesByOwnedConversation(@Param("tenantId") String tenantId,
                                                                      @Param("ownerId") String ownerId,
                                                                      @Param("conversationId") String conversationId);

    @Update("""
        UPDATE conversation_message m
        JOIN conversation c ON c.id = m.conversation_id
        SET m.status = #{toStatus},
            m.updated_at = #{updatedAt},
            m.version = m.version + 1
        WHERE c.tenant_id = #{tenantId}
          AND c.owner_id = #{ownerId}
          AND c.deleted_at IS NULL
          AND m.id = #{assistantMessageId}
          AND m.role = 'ASSISTANT'
          AND m.status = #{fromStatus}
        """)
    int updateAssistantStatusOwned(@Param("tenantId") String tenantId,
                                   @Param("ownerId") String ownerId,
                                   @Param("assistantMessageId") String assistantMessageId,
                                   @Param("fromStatus") String fromStatus,
                                   @Param("toStatus") String toStatus,
                                   @Param("updatedAt") Instant updatedAt);

    @Update("""
        UPDATE conversation_message m
        JOIN conversation c ON c.id = m.conversation_id
        SET m.status = 'COMPLETED',
            m.content = #{finalContent},
            m.stream_snapshot = #{snapshotJson},
            m.payload_version = #{payloadVersion},
            m.error_code = NULL,
            m.error_message = NULL,
            m.updated_at = #{updatedAt},
            m.version = m.version + 1
        WHERE c.tenant_id = #{tenantId}
          AND c.owner_id = #{ownerId}
          AND c.deleted_at IS NULL
          AND m.id = #{assistantMessageId}
          AND m.role = 'ASSISTANT'
          AND m.status = 'STREAMING'
        """)
    int completeAssistantOwned(@Param("tenantId") String tenantId,
                               @Param("ownerId") String ownerId,
                               @Param("assistantMessageId") String assistantMessageId,
                               @Param("finalContent") String finalContent,
                               @Param("snapshotJson") String snapshotJson,
                               @Param("payloadVersion") Integer payloadVersion,
                               @Param("updatedAt") Instant updatedAt);

    @Update("""
        UPDATE conversation_message m
        JOIN conversation c ON c.id = m.conversation_id
        SET m.status = #{toStatus},
            m.error_code = #{errorCode},
            m.error_message = #{errorMessage},
            m.stream_snapshot = #{partialSnapshotJson},
            m.payload_version = #{payloadVersion},
            m.updated_at = #{updatedAt},
            m.version = m.version + 1
        WHERE c.tenant_id = #{tenantId}
          AND c.owner_id = #{ownerId}
          AND c.deleted_at IS NULL
          AND m.id = #{assistantMessageId}
          AND m.role = 'ASSISTANT'
          AND m.status = #{fromStatus}
        """)
    int failAssistantOwned(@Param("tenantId") String tenantId,
                           @Param("ownerId") String ownerId,
                           @Param("assistantMessageId") String assistantMessageId,
                           @Param("fromStatus") String fromStatus,
                           @Param("toStatus") String toStatus,
                           @Param("errorCode") String errorCode,
                           @Param("errorMessage") String errorMessage,
                           @Param("partialSnapshotJson") String partialSnapshotJson,
                           @Param("payloadVersion") Integer payloadVersion,
                           @Param("updatedAt") Instant updatedAt);
}