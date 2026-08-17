package com.jd.genie.platform.phase2.configuration.team.mapper;

import com.jd.genie.platform.phase2.configuration.team.entity.AgentTeamMemberEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AgentTeamMemberMapper {

    @Insert("""
        <script>
        INSERT INTO agent_team_member(tenant_id, owner_id, team_id, agent_id, sort_order, created_at)
        VALUES
        <foreach collection="members" item="member" separator=",">
          (#{member.tenantId}, #{member.ownerId}, #{member.teamId}, #{member.agentId}, #{member.sortOrder}, #{member.createdAt})
        </foreach>
        </script>
        """)
    int batchInsert(@Param("members") List<AgentTeamMemberEntity> members);

    @Select("""
        SELECT *
        FROM agent_team_member
        WHERE tenant_id = #{tenantId}
          AND owner_id = #{ownerId}
          AND team_id = #{teamId}
        ORDER BY sort_order ASC
        """)
    List<AgentTeamMemberEntity> selectOwnedMembersByTeam(@Param("tenantId") String tenantId,
                                                         @Param("ownerId") String ownerId,
                                                         @Param("teamId") String teamId);

    @Delete("""
        DELETE FROM agent_team_member
        WHERE tenant_id = #{tenantId}
          AND owner_id = #{ownerId}
          AND team_id = #{teamId}
        """)
    int deleteOwnedMembersByTeam(@Param("tenantId") String tenantId,
                                 @Param("ownerId") String ownerId,
                                 @Param("teamId") String teamId);
}
