package com.jd.genie.platform.user.mapper;

import com.jd.genie.platform.user.entity.TenantEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TenantMapper {

    @Select("SELECT id, code, name, status, created_at, updated_at FROM app_tenant WHERE code = #{code}")
    TenantEntity findByCode(@Param("code") String code);

    @Insert("INSERT INTO app_tenant (id, code, name, status, created_at, updated_at) "
        + "VALUES (#{id}, #{code}, #{name}, #{status}, #{createdAt}, #{updatedAt})")
    int insert(TenantEntity tenant);
}
