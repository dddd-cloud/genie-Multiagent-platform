package com.jd.genie.platform.phase2.tooling;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface McpToolMapper {
    @Select("SELECT COUNT(*) FROM mcp_tool")
    long countAll();
}
