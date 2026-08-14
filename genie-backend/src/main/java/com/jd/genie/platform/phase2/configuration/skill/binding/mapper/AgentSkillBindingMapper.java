package com.jd.genie.platform.phase2.configuration.skill.binding.mapper;

import com.jd.genie.platform.phase2.configuration.skill.binding.AgentRuntimeSkillSnapshot;
import com.jd.genie.platform.phase2.configuration.skill.binding.entity.AgentSkillBindingEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AgentSkillBindingMapper {

    @Insert("""
        INSERT INTO agent_skill_binding(tenant_id, owner_id, agent_id, skill_id, sort_order, created_at)
        VALUES (#{entity.tenantId}, #{entity.ownerId}, #{entity.agentId}, #{entity.skillId}, #{entity.sortOrder}, #{entity.createdAt})
        """)
    int insertBinding(@Param("entity") AgentSkillBindingEntity entity);

    @Insert("""
        <script>
        INSERT INTO agent_skill_binding(tenant_id, owner_id, agent_id, skill_id, sort_order, created_at)
        VALUES
        <foreach collection="bindings" item="binding" separator=",">
          (#{binding.tenantId}, #{binding.ownerId}, #{binding.agentId}, #{binding.skillId}, #{binding.sortOrder}, #{binding.createdAt})
        </foreach>
        </script>
        """)
    int batchInsert(@Param("bindings") List<AgentSkillBindingEntity> bindings);

    @Select("""
        SELECT *
        FROM agent_skill_binding
        WHERE tenant_id = #{tenantId}
          AND owner_id = #{ownerId}
          AND agent_id = #{agentId}
        ORDER BY sort_order ASC
        """)
    List<AgentSkillBindingEntity> selectOwnedBindingsByAgent(@Param("tenantId") String tenantId,
                                                             @Param("ownerId") String ownerId,
                                                             @Param("agentId") String agentId);

    @Results(id = "AgentRuntimeSkillSnapshotMap", value = {
        @Result(column = "skill_id", property = "skillId"),
        @Result(column = "skill_version", property = "skillVersion"),
        @Result(column = "sort_order", property = "sortOrder"),
        @Result(column = "skill_name", property = "skillName"),
        @Result(column = "description", property = "description"),
        @Result(column = "instruction", property = "instruction"),
        @Result(column = "output_requirement", property = "outputRequirement"),
        @Result(column = "status", property = "status")
    })
    @Select("""
        SELECT s.id AS skill_id,
               s.version AS skill_version,
               b.sort_order AS sort_order,
               s.name AS skill_name,
               s.description AS description,
               s.instruction AS instruction,
               s.output_requirement AS output_requirement,
               s.status AS status
        FROM agent_skill_binding b
        JOIN skill_definition s
          ON s.id = b.skill_id
         AND s.tenant_id = b.tenant_id
         AND s.owner_id = b.owner_id
         AND s.deleted_at IS NULL
        WHERE b.tenant_id = #{tenantId}
          AND b.owner_id = #{ownerId}
          AND b.agent_id = #{agentId}
        ORDER BY b.sort_order ASC
        """)
    List<AgentRuntimeSkillSnapshot> selectOwnedRuntimeSkillSnapshots(@Param("tenantId") String tenantId,
                                                                     @Param("ownerId") String ownerId,
                                                                     @Param("agentId") String agentId);

    @Delete("""
        DELETE FROM agent_skill_binding
        WHERE tenant_id = #{tenantId}
          AND owner_id = #{ownerId}
          AND agent_id = #{agentId}
        """)
    int deleteOwnedBindingsByAgent(@Param("tenantId") String tenantId,
                                   @Param("ownerId") String ownerId,
                                   @Param("agentId") String agentId);

    @Select("""
        SELECT COUNT(1)
        FROM agent_skill_binding
        WHERE tenant_id = #{tenantId}
          AND owner_id = #{ownerId}
          AND skill_id = #{skillId}
        """)
    long countOwnedReferencesBySkill(@Param("tenantId") String tenantId,
                                      @Param("ownerId") String ownerId,
                                      @Param("skillId") String skillId);
}
