package com.jd.genie.platform.phase2.configuration.team.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jd.genie.platform.phase2.configuration.team.entity.AgentTeamEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.List;

@Mapper
public interface AgentTeamMapper extends BaseMapper<AgentTeamEntity> {

    @Select("""
        SELECT *
        FROM agent_team
        WHERE id = #{teamId}
          AND tenant_id = #{tenantId}
          AND owner_id = #{ownerId}
          AND deleted_at IS NULL
        """)
    AgentTeamEntity selectOwnedById(@Param("tenantId") String tenantId,
                                    @Param("ownerId") String ownerId,
                                    @Param("teamId") String teamId);

    @Select("""
        SELECT *
        FROM agent_team
        WHERE tenant_id = #{tenantId}
          AND owner_id = #{ownerId}
          AND deleted_at IS NULL
        ORDER BY updated_at DESC, id DESC
        LIMIT #{limit} OFFSET #{offset}
        """)
    List<AgentTeamEntity> selectOwnedPage(@Param("tenantId") String tenantId,
                                          @Param("ownerId") String ownerId,
                                          @Param("limit") int limit,
                                          @Param("offset") int offset);

    @Select("""
        SELECT COUNT(1) > 0
        FROM agent_team
        WHERE tenant_id = #{tenantId}
          AND owner_id = #{ownerId}
          AND name = #{name}
          AND deleted_at IS NULL
          AND (#{excludeTeamId,jdbcType=VARCHAR} IS NULL OR id <> #{excludeTeamId,jdbcType=VARCHAR})
        """)
    boolean existsOwnedActiveName(@Param("tenantId") String tenantId,
                                  @Param("ownerId") String ownerId,
                                  @Param("name") String name,
                                  @Param("excludeTeamId") String excludeTeamId);

    @Update("""
        UPDATE agent_team
        SET name = #{entity.name},
            description = #{entity.description},
            master_agent_id = #{entity.masterAgentId},
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
                               @Param("entity") AgentTeamEntity entity,
                               @Param("version") Long version,
                               @Param("updatedAt") Instant updatedAt);

    @Update("""
        UPDATE agent_team
        SET deleted_at = #{deletedAt},
            updated_at = #{deletedAt},
            version = version + 1
        WHERE id = #{teamId}
          AND tenant_id = #{tenantId}
          AND owner_id = #{ownerId}
          AND version = #{version}
          AND deleted_at IS NULL
        """)
    int softDeleteOwnedWithVersion(@Param("tenantId") String tenantId,
                                   @Param("ownerId") String ownerId,
                                   @Param("teamId") String teamId,
                                   @Param("version") Long version,
                                   @Param("deletedAt") Instant deletedAt);
}
