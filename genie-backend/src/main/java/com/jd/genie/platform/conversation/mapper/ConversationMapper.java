package com.jd.genie.platform.conversation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jd.genie.platform.conversation.entity.ConversationEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.List;

@Mapper
public interface ConversationMapper extends BaseMapper<ConversationEntity> {

    @Select("""
        SELECT *
        FROM conversation
        WHERE id = #{conversationId}
          AND tenant_id = #{tenantId}
          AND owner_id = #{ownerId}
          AND deleted_at IS NULL
        """)
    ConversationEntity selectOwnedConversation(@Param("tenantId") String tenantId,
                                               @Param("ownerId") String ownerId,
                                               @Param("conversationId") String conversationId);

    @Select("""
        SELECT *
        FROM conversation
        WHERE id = #{conversationId}
          AND tenant_id = #{tenantId}
          AND owner_id = #{ownerId}
          AND deleted_at IS NULL
        FOR UPDATE
        """)
    ConversationEntity selectOwnedConversationForUpdate(@Param("tenantId") String tenantId,
                                                        @Param("ownerId") String ownerId,
                                                        @Param("conversationId") String conversationId);

    @Select("""
        SELECT *
        FROM conversation
        WHERE tenant_id = #{tenantId}
          AND owner_id = #{ownerId}
          AND deleted_at IS NULL
        ORDER BY (last_message_at IS NULL) ASC,
                 last_message_at DESC,
                 created_at DESC,
                 id DESC
        LIMIT #{limit} OFFSET #{offset}
        """)
    List<ConversationEntity> selectOwnedConversationPage(@Param("tenantId") String tenantId,
                                                         @Param("ownerId") String ownerId,
                                                         @Param("limit") int limit,
                                                         @Param("offset") int offset);

    @Update("""
        UPDATE conversation
        SET title = #{title}, updated_at = #{updatedAt}, version = version + 1
        WHERE id = #{conversationId}
          AND tenant_id = #{tenantId}
          AND owner_id = #{ownerId}
          AND deleted_at IS NULL
        """)
    int updateTitleOwned(@Param("tenantId") String tenantId,
                         @Param("ownerId") String ownerId,
                         @Param("conversationId") String conversationId,
                         @Param("title") String title,
                         @Param("updatedAt") Instant updatedAt);

    @Update("""
        UPDATE conversation
        SET deleted_at = #{deletedAt}, updated_at = #{deletedAt}, version = version + 1
        WHERE id = #{conversationId}
          AND tenant_id = #{tenantId}
          AND owner_id = #{ownerId}
          AND deleted_at IS NULL
        """)
    int softDeleteOwned(@Param("tenantId") String tenantId,
                        @Param("ownerId") String ownerId,
                        @Param("conversationId") String conversationId,
                        @Param("deletedAt") Instant deletedAt);

    @Update("""
        UPDATE conversation
        SET next_turn_no = next_turn_no + 1,
            last_message_at = #{lastMessageAt},
            updated_at = #{updatedAt},
            version = version + 1
        WHERE id = #{conversationId}
          AND tenant_id = #{tenantId}
          AND owner_id = #{ownerId}
          AND deleted_at IS NULL
        """)
    int advanceConversationTurn(@Param("tenantId") String tenantId,
                                @Param("ownerId") String ownerId,
                                @Param("conversationId") String conversationId,
                                @Param("lastMessageAt") Instant lastMessageAt,
                                @Param("updatedAt") Instant updatedAt);


    @Update("""
        UPDATE conversation
        SET title = #{title},
            updated_at = #{updatedAt},
            version = version + 1
        WHERE id = #{conversationId}
          AND tenant_id = #{tenantId}
          AND owner_id = #{ownerId}
          AND deleted_at IS NULL
          AND title = #{defaultTitle}
          AND next_turn_no = #{expectedNextTurnNoAfterPrepare}
        """)
    int autoTitleFirstTurnIfDefault(@Param("tenantId") String tenantId,
                                    @Param("ownerId") String ownerId,
                                    @Param("conversationId") String conversationId,
                                    @Param("expectedNextTurnNoAfterPrepare") Long expectedNextTurnNoAfterPrepare,
                                    @Param("defaultTitle") String defaultTitle,
                                    @Param("title") String title,
                                    @Param("updatedAt") Instant updatedAt);
    @Update("""
        UPDATE conversation
        SET next_turn_no = next_turn_no + 1,
            last_message_at = #{lastMessageAt},
            updated_at = #{updatedAt},
            title = CASE
                WHEN #{generatedTitle} IS NULL THEN title
                ELSE #{generatedTitle}
            END,
            version = version + 1
        WHERE id = #{conversationId}
          AND tenant_id = #{tenantId}
          AND owner_id = #{ownerId}
          AND deleted_at IS NULL
          AND next_turn_no = #{expectedNextTurnNo}
        """)
    int completePrepareExecution(@Param("tenantId") String tenantId,
                                 @Param("ownerId") String ownerId,
                                 @Param("conversationId") String conversationId,
                                 @Param("expectedNextTurnNo") Long expectedNextTurnNo,
                                 @Param("lastMessageAt") Instant lastMessageAt,
                                 @Param("updatedAt") Instant updatedAt,
                                 @Param("generatedTitle") String generatedTitle);
}
