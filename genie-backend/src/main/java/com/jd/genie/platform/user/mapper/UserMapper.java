package com.jd.genie.platform.user.mapper;

import com.jd.genie.platform.user.entity.UserEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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

    @Insert("INSERT INTO app_user (id, tenant_id, username, display_name, password_hash, role, status, created_at, updated_at, version) "
        + "VALUES (#{id}, #{tenantId}, #{username}, #{displayName}, #{passwordHash}, #{role}, #{status}, "
        + "#{createdAt}, #{updatedAt}, #{version})")
    int insert(UserEntity user);
}
