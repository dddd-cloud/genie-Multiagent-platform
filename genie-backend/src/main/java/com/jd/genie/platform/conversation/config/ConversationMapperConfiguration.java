package com.jd.genie.platform.conversation.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.jd.genie.platform.conversation.mapper")
public class ConversationMapperConfiguration {
}
