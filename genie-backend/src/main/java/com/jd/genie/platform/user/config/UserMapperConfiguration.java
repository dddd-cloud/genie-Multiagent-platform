package com.jd.genie.platform.user.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * GenieApplication scans only the legacy com.jd.genie.mapper package. This supplemental scan
 * registers MVP-A mappers while preserving the frozen legacy scan unchanged.
 */
@Configuration
@MapperScan("com.jd.genie.platform.user.mapper")
public class UserMapperConfiguration {
}
