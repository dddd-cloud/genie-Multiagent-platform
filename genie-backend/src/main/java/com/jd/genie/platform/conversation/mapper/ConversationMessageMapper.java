package com.jd.genie.platform.conversation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jd.genie.platform.conversation.dto.ConversationMessagePreviewRow;
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

    @Select("""
        <script>
        SELECT ranked.conversation_id AS conversationId,
               ranked.content AS content
        FROM (
            SELECT m.conversation_id,
                   m.content,
                   ROW_NUMBER() OVER (
                       PARTITION BY m.conversation_id
                       ORDER BY m.turn_no DESC, m.created_at DESC, m.id DESC
                   ) AS row_num
            FROM conversation_message m
            JOIN conversation c ON c.id = m.conversation_id
            WHERE c.tenant_id = #{tenantId}
              AND c.owner_id = #{ownerId}
              AND c.deleted_at IS NULL
              AND m.role = 'USER'
              AND m.conversation_id IN
              <foreach collection="conversationIds" item="conversationId" open="(" separator="," close=")">
                #{conversationId}
              </foreach>
        ) ranked
        WHERE ranked.row_num = 1
        </script>
        """)
    List<ConversationMessagePreviewRow> selectLatestUserPreviews(@Param("tenantId") String tenantId,
                                                                 @Param("ownerId") String ownerId,
                                                                 @Param("conversationIds") List<String> conversationIds);

    @Select("""
        SELECT m.*
        FROM conversation_message m
        JOIN conversation c ON c.id = m.conversation_id
        JOIN (
            SELECT turn_no
            FROM conversation_message
            WHERE conversation_id = #{conversationId}
            GROUP BY turn_no
            ORDER BY turn_no DESC
            LIMIT 50
        ) recent_turns ON recent_turns.turn_no = m.turn_no
        WHERE c.tenant_id = #{tenantId}
          AND c.owner_id = #{ownerId}
          AND c.deleted_at IS NULL
          AND m.conversation_id = #{conversationId}
        ORDER BY m.turn_no ASC,
                 CASE m.role WHEN 'USER' THEN 0 WHEN 'ASSISTANT' THEN 1 ELSE 2 END ASC,
                 m.created_at ASC,
                 m.id ASC
        LIMIT 100
        """)
    List<ConversationMessageEntity> selectRecentMessagesByOwnedConversation(@Param("tenantId") String tenantId,
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
