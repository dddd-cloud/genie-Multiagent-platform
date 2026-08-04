package com.jd.genie.platform.phase2.configuration.agent.mapper;

import com.jd.genie.platform.phase2.configuration.agent.entity.AgentSkillBindingEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
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