package com.jd.genie.platform.settings.mapper;

import com.jd.genie.platform.settings.entity.UserSettingEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * Every statement is scoped by tenant_id AND user_id: a setting is never readable
 * or writable across owners even if a caller supplies a foreign key.
 */
@Mapper
public interface UserSettingMapper {

    @Select("SELECT id, tenant_id, user_id, setting_key, setting_value, created_at, updated_at "
        + "FROM user_setting WHERE tenant_id = #{tenantId} AND user_id = #{userId} ORDER BY setting_key")
    List<UserSettingEntity> findByOwner(@Param("tenantId") String tenantId, @Param("userId") String userId);

    @Insert("INSERT INTO user_setting (id, tenant_id, user_id, setting_key, setting_value, created_at, updated_at) "
        + "VALUES (#{id}, #{tenantId}, #{userId}, #{settingKey}, #{settingValue}, #{createdAt}, #{updatedAt}) "
        + "ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value), updated_at = VALUES(updated_at)")
    int upsert(UserSettingEntity setting);

    @Delete("DELETE FROM user_setting "
        + "WHERE tenant_id = #{tenantId} AND user_id = #{userId} AND setting_key = #{settingKey}")
    int deleteByOwnerAndKey(@Param("tenantId") String tenantId, @Param("userId") String userId,
                            @Param("settingKey") String settingKey);
}
