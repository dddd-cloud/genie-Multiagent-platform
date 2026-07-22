package com.jd.genie.platform.conversation.service;

import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.conversation.mapper.ConversationMessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ConversationRecoveryService {
    private final ConversationMessageMapper conversationMessageMapper;
    private final Clock clock = Clock.systemUTC();

    @Transactional
    public int recoverInterruptedAssistants() {
        return conversationMessageMapper.interruptAllActiveAssistantsOnStartup(
            "ASSISTANT",
            "PENDING",
            "STREAMING",
            "INTERRUPTED",
            MvpErrorCode.SERVICE_RESTARTED.name(),
            null,
            Instant.now(clock)
        );
    }
}