package com.jd.genie.platform.phase2.config;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * Register Phase2 MyBatis mappers (A agent/skill + B MCP tooling).
 * annotationClass=@Mapper avoids registering non-mapper interfaces in tooling
 * (e.g. McpClientAdapter) as mapper beans.
 */
@Configuration
@MapperScan(
    basePackages = {
        "com.jd.genie.platform.phase2.configuration.agent.mapper",
        "com.jd.genie.platform.phase2.configuration.skill.mapper",
        "com.jd.genie.platform.phase2.tooling",
    },
    annotationClass = Mapper.class
)
public class Phase2MapperConfiguration {
}
