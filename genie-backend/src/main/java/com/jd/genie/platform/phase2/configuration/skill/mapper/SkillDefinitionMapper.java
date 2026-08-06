package com.jd.genie.platform.phase2.configuration.skill.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jd.genie.platform.phase2.configuration.skill.entity.SkillDefinitionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.List;

@Mapper
public interface SkillDefinitionMapper extends BaseMapper<SkillDefinitionEntity> {

    @Select("""
        SELECT *
        FROM skill_definition
        WHERE id = #{skillId}
          AND tenant_id = #{tenantId}
          AND owner_id = #{ownerId}
          AND deleted_at IS NULL
        """)
    SkillDefinitionEntity selectOwnedById(@Param("tenantId") String tenantId,
                                           @Param("ownerId") String ownerId,
                                           @Param("skillId") String skillId);

    @Select("""
        SELECT *
        FROM skill_definition
        WHERE tenant_id = #{tenantId}
          AND owner_id = #{ownerId}
          AND deleted_at IS NULL
        ORDER BY updated_at DESC, id DESC
        LIMIT #{limit} OFFSET #{offset}
        """)
    List<SkillDefinitionEntity> selectOwnedPage(@Param("tenantId") String tenantId,
                                                @Param("ownerId") String ownerId,
                                                @Param("limit") int limit,
                                                @Param("offset") int offset);

    @Select("""
        SELECT COUNT(1)
        FROM skill_definition
        WHERE tenant_id = #{tenantId}
          AND owner_id = #{ownerId}
          AND deleted_at IS NULL
        """)
    long countOwned(@Param("tenantId") String tenantId,
                    @Param("ownerId") String ownerId);

    @Select("""
        SELECT version
        FROM skill_definition
        WHERE id = #{skillId}
          AND tenant_id = #{tenantId}
          AND owner_id = #{ownerId}
          AND deleted_at IS NULL
        """)
    Long selectOwnedVersion(@Param("tenantId") String tenantId,
                            @Param("ownerId") String ownerId,
                            @Param("skillId") String skillId);

    @Select("""
        SELECT COUNT(1) > 0
        FROM skill_definition
        WHERE tenant_id = #{tenantId}
          AND owner_id = #{ownerId}
          AND name = #{name}
          AND deleted_at IS NULL
          AND (#{excludeSkillId,jdbcType=VARCHAR} IS NULL OR id <> #{excludeSkillId,jdbcType=VARCHAR})
        """)
    boolean existsOwnedActiveName(@Param("tenantId") String tenantId,
                                  @Param("ownerId") String ownerId,
                                  @Param("name") String name,
                                  @Param("excludeSkillId") String excludeSkillId);

    @Select("""
        <script>
        SELECT *
        FROM skill_definition
        WHERE tenant_id = #{tenantId}
          AND owner_id = #{ownerId}
          AND deleted_at IS NULL
          AND id IN
          <foreach collection="skillIds" item="skillId" open="(" separator="," close=")">
            #{skillId}
          </foreach>
        ORDER BY updated_at DESC, id DESC
        </script>
        """)
    List<SkillDefinitionEntity> selectOwnedByIds(@Param("tenantId") String tenantId,
                                                 @Param("ownerId") String ownerId,
                                                 @Param("skillIds") List<String> skillIds);

    @Update("""
        UPDATE skill_definition
        SET name = #{entity.name},
            description = #{entity.description},
            instruction = #{entity.instruction},
            output_requirement = #{entity.outputRequirement,jdbcType=VARCHAR},
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
                               @Param("entity") SkillDefinitionEntity entity,
                               @Param("version") Long version,
                               @Param("updatedAt") Instant updatedAt);

    @Update("""
        UPDATE skill_definition
        SET status = #{status},
            updated_at = #{updatedAt},
            version = version + 1
        WHERE id = #{skillId}
          AND tenant_id = #{tenantId}
          AND owner_id = #{ownerId}
          AND version = #{version}
          AND deleted_at IS NULL
        """)
    int updateStatusOwnedWithVersion(@Param("tenantId") String tenantId,
                                     @Param("ownerId") String ownerId,
                                     @Param("skillId") String skillId,
                                     @Param("version") Long version,
                                     @Param("status") String status,
                                     @Param("updatedAt") Instant updatedAt);

    @Update("""
        UPDATE skill_definition
        SET deleted_at = #{deletedAt},
            updated_at = #{deletedAt},
            version = version + 1
        WHERE id = #{skillId}
          AND tenant_id = #{tenantId}
          AND owner_id = #{ownerId}
          AND version = #{version}
          AND deleted_at IS NULL
        """)
    int softDeleteOwnedWithVersion(@Param("tenantId") String tenantId,
                                   @Param("ownerId") String ownerId,
                                   @Param("skillId") String skillId,
                                   @Param("version") Long version,
                                   @Param("deletedAt") Instant deletedAt);
}