package com.jd.genie.platform.user.mapper;

import com.jd.genie.platform.user.entity.UserEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.util.List;

@Mapper
public interface UserMapper {

    @Select("SELECT id, tenant_id, username, display_name, password_hash, role, status, created_at, updated_at, version "
        + "FROM app_user WHERE tenant_id = #{tenantId} AND username = #{username}")
    UserEntity findByTenantIdAndUsername(@Param("tenantId") String tenantId, @Param("username") String username);

    @Select("SELECT id, tenant_id, username, display_name, password_hash, role, status, created_at, updated_at, version "
        + "FROM app_user WHERE username = #{username} AND status = 'ACTIVE' LIMIT 1")
    UserEntity findActiveByUsername(@Param("username") String username);

    @Select("SELECT COUNT(*) FROM app_user")
    long countAll();

    @Select("SELECT id, tenant_id, username, display_name, password_hash, role, status, created_at, updated_at, version "
        + "FROM app_user WHERE id = #{userId} AND tenant_id = #{tenantId}")
    UserEntity findByIdAndTenantId(@Param("userId") String userId, @Param("tenantId") String tenantId);

    /**
     * Admin list with optional filters. The keyword pattern is built by the caller so LIKE wildcards
     * inside the raw keyword can be escaped before they reach SQL.
     */
    @Select("<script>"
        + "SELECT id, tenant_id, username, display_name, password_hash, role, status, created_at, updated_at, version "
        + "FROM app_user WHERE tenant_id = #{tenantId} "
        + "<if test='keywordPattern != null'>AND (username LIKE #{keywordPattern} OR display_name LIKE #{keywordPattern}) </if>"
        + "<if test='role != null'>AND role = #{role} </if>"
        + "<if test='status != null'>AND status = #{status} </if>"
        + "ORDER BY created_at DESC, id DESC LIMIT #{limit} OFFSET #{offset}"
        + "</script>")
    List<UserEntity> searchByTenant(@Param("tenantId") String tenantId,
                                    @Param("keywordPattern") String keywordPattern,
                                    @Param("role") String role,
                                    @Param("status") String status,
                                    @Param("offset") int offset,
                                    @Param("limit") int limit);

    @Update("UPDATE app_user SET status = #{status}, updated_at = #{updatedAt}, version = version + 1 "
        + "WHERE id = #{userId} AND tenant_id = #{tenantId}")
    int updateStatusByIdAndTenantId(@Param("userId") String userId, @Param("tenantId") String tenantId,
                                    @Param("status") String status, @Param("updatedAt") java.time.LocalDateTime updatedAt);

    @Update("UPDATE app_user SET password_hash = #{passwordHash}, updated_at = #{updatedAt}, version = version + 1 "
        + "WHERE id = #{userId} AND tenant_id = #{tenantId}")
    int updatePasswordByIdAndTenantId(@Param("userId") String userId, @Param("tenantId") String tenantId,
                                      @Param("passwordHash") String passwordHash, @Param("updatedAt") java.time.LocalDateTime updatedAt);

    @Insert("INSERT INTO app_user (id, tenant_id, username, display_name, password_hash, role, status, created_at, updated_at, version) "
        + "VALUES (#{id}, #{tenantId}, #{username}, #{displayName}, #{passwordHash}, #{role}, #{status}, "
        + "#{createdAt}, #{updatedAt}, #{version})")
    int insert(UserEntity user);
}
