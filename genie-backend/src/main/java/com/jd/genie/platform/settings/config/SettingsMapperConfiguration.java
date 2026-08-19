package com.jd.genie.platform.settings.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * Supplemental mapper scan for the settings domain; mirrors UserMapperConfiguration so the frozen
 * legacy scan in GenieApplication stays untouched.
 */
@Configuration
@MapperScan("com.jd.genie.platform.settings.mapper")
public class SettingsMapperConfiguration {
}
