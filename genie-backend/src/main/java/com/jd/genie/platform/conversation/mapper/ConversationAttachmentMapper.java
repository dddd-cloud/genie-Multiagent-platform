package com.jd.genie.platform.conversation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jd.genie.platform.conversation.entity.ConversationAttachmentEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ConversationAttachmentMapper extends BaseMapper<ConversationAttachmentEntity> {

    @Select("""
        SELECT *
        FROM conversation_attachment
        WHERE id = #{attachmentId}
          AND conversation_id = #{conversationId}
          AND tenant_id = #{tenantId}
          AND owner_id = #{ownerId}
        """)
    ConversationAttachmentEntity selectOwned(
        @Param("tenantId") String tenantId,
        @Param("ownerId") String ownerId,
        @Param("conversationId") String conversationId,
        @Param("attachmentId") String attachmentId
    );

    @Select("""
        <script>
        SELECT *
        FROM conversation_attachment
        WHERE conversation_id = #{conversationId}
          AND tenant_id = #{tenantId}
          AND owner_id = #{ownerId}
          AND id IN
          <foreach collection="ids" item="id" open="(" separator="," close=")">
            #{id}
          </foreach>
        </script>
        """)
    List<ConversationAttachmentEntity> selectOwnedByIds(
        @Param("tenantId") String tenantId,
        @Param("ownerId") String ownerId,
        @Param("conversationId") String conversationId,
        @Param("ids") List<String> ids
    );
}
