package com.jd.genie.platform.phase2.tooling;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/** B-owned mapper boundary; transactional orchestration remains in McpServerService. */
@Mapper
public interface McpServerMapper {
    @Select("SELECT COUNT(*) FROM mcp_server")
    long countAll();
}
