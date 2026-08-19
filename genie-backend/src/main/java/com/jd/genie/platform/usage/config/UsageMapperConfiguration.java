package com.jd.genie.platform.usage.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * Supplemental mapper scan for the usage domain; mirrors UserMapperConfiguration so the frozen
 * legacy scan in GenieApplication stays untouched.
 */
@Configuration
@MapperScan("com.jd.genie.platform.usage.mapper")
public class UsageMapperConfiguration {
}
