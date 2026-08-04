package com.jd.genie.platform.phase2.configuration.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jd.genie.platform.phase2.configuration.agent.entity.AgentDefinitionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.List;

@Mapper
public interface AgentDefinitionMapper extends BaseMapper<AgentDefinitionEntity> {

    @Select("""
        SELECT *
        FROM agent_definition
        WHERE id = #{agentId}
          AND tenant_id = #{tenantId}
          AND owner_id = #{ownerId}
          AND deleted_at IS NULL
        """)
    AgentDefinitionEntity selectOwnedById(@Param("tenantId") String tenantId,
                                           @Param("ownerId") String ownerId,
                                           @Param("agentId") String agentId);

    @Select("""
        SELECT *
        FROM agent_definition
        WHERE tenant_id = #{tenantId}
          AND owner_id = #{ownerId}
          AND deleted_at IS NULL
        ORDER BY updated_at DESC, id DESC
        LIMIT #{limit} OFFSET #{offset}
        """)
    List<AgentDefinitionEntity> selectOwnedPage(@Param("tenantId") String tenantId,
                                                @Param("ownerId") String ownerId,
                                                @Param("limit") int limit,
                                                @Param("offset") int offset);

    @Select("""
        SELECT COUNT(1)
        FROM agent_definition
        WHERE tenant_id = #{tenantId}
          AND owner_id = #{ownerId}
          AND deleted_at IS NULL
        """)
    long countOwned(@Param("tenantId") String tenantId,
                    @Param("ownerId") String ownerId);

    @Select("""
        SELECT version
        FROM agent_definition
        WHERE id = #{agentId}
          AND tenant_id = #{tenantId}
          AND owner_id = #{ownerId}
          AND deleted_at IS NULL
        """)
    Long selectOwnedVersion(@Param("tenantId") String tenantId,
                            @Param("ownerId") String ownerId,
                            @Param("agentId") String agentId);

    @Select("""
        SELECT COUNT(1) > 0
        FROM agent_definition
        WHERE tenant_id = #{tenantId}
          AND owner_id = #{ownerId}
          AND name = #{name}
          AND deleted_at IS NULL
          AND (#{excludeAgentId,jdbcType=VARCHAR} IS NULL OR id <> #{excludeAgentId,jdbcType=VARCHAR})
        """)
    boolean existsOwnedActiveName(@Param("tenantId") String tenantId,
                                  @Param("ownerId") String ownerId,
                                  @Param("name") String name,
                                  @Param("excludeAgentId") String excludeAgentId);

    @Select("""
        <script>
        SELECT *
        FROM agent_definition
        WHERE tenant_id = #{tenantId}
          AND owner_id = #{ownerId}
          AND deleted_at IS NULL
          AND id IN
          <foreach collection="agentIds" item="agentId" open="(" separator="," close=")">
            #{agentId}
          </foreach>
        ORDER BY updated_at DESC, id DESC
        </script>
        """)
    List<AgentDefinitionEntity> selectOwnedByIds(@Param("tenantId") String tenantId,
                                                 @Param("ownerId") String ownerId,
                                                 @Param("agentIds") List<String> agentIds);

    @Update("""
        UPDATE agent_definition
        SET name = #{entity.name},
            description = #{entity.description},
            prompt_mode = #{entity.promptMode},
            prompt_config = #{entity.promptConfig,jdbcType=VARCHAR},
            system_prompt = #{entity.systemPrompt},
            model_name = #{entity.modelName,jdbcType=VARCHAR},
            updated_at = #{updatedAt},
            version = version + 1
        WHERE id = #{entity.id}
          AND tenant_id = #{tenantId}
          AND owner_id = #{ownerId}
          AND version = #{version}
          AND deleted_at IS NULL
        """)
    int updateOwnedWithVersion(@Param("tenantId") String tenantId,
                               @Param("ownerId") String ownerId,
                               @Param("entity") AgentDefinitionEntity entity,
                               @Param("version") Long version,
                               @Param("updatedAt") Instant updatedAt);

    @Update("""
        UPDATE agent_definition
        SET status = #{status},
            updated_at = #{updatedAt},
            version = version + 1
        WHERE id = #{agentId}
          AND tenant_id = #{tenantId}
          AND owner_id = #{ownerId}
          AND version = #{version}
          AND deleted_at IS NULL
        """)
    int updateStatusOwnedWithVersion(@Param("tenantId") String tenantId,
                                     @Param("ownerId") String ownerId,
                                     @Param("agentId") String agentId,
                                     @Param("version") Long version,
                                     @Param("status") String status,
                                     @Param("updatedAt") Instant updatedAt);

    @Update("""
        UPDATE agent_definition
        SET deleted_at = #{deletedAt},
            updated_at = #{deletedAt},
            version = version + 1
        WHERE id = #{agentId}
          AND tenant_id = #{tenantId}
          AND owner_id = #{ownerId}
          AND version = #{version}
          AND deleted_at IS NULL
        """)
    int softDeleteOwnedWithVersion(@Param("tenantId") String tenantId,
                                   @Param("ownerId") String ownerId,
                                   @Param("agentId") String agentId,
                                   @Param("version") Long version,
                                   @Param("deletedAt") Instant deletedAt);
}