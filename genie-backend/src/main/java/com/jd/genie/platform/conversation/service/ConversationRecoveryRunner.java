package com.jd.genie.platform.conversation.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnExpression("!'${spring.datasource.url:}'.startsWith('jdbc:h2:')")
@Order(0)
@RequiredArgsConstructor
public class ConversationRecoveryRunner implements ApplicationRunner {
    private final ConversationRecoveryService conversationRecoveryService;

    @Override
    public void run(ApplicationArguments args) {
        conversationRecoveryService.recoverInterruptedAssistants();
    }
}