package com.jd.genie.platform.agentbridge;

import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.conversation.exception.ConversationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class AgentBridgeErrorMapperTest {

    @Test
    void preservesBridgeAndConversationFrozenCodes() {
        AgentBridgeException bridge = new AgentBridgeException(MvpErrorCode.SNAPSHOT_TOO_LARGE, "too large");
        ConversationException conversation = new ConversationException(MvpErrorCode.SNAPSHOT_INVALID, "invalid");

        assertEquals(MvpErrorCode.SNAPSHOT_TOO_LARGE,
                AgentBridgeErrorMapper.errorCode(bridge, MvpErrorCode.INTERNAL_ERROR));
        assertEquals(MvpErrorCode.SNAPSHOT_INVALID,
                AgentBridgeErrorMapper.errorCode(conversation, MvpErrorCode.INTERNAL_ERROR));
    }

    @Test
    void mapsDataAccessFailuresAndKeepsTheOriginalCauseForMvcAdvice() {
        DataAccessResourceFailureException databaseFailure =
                new DataAccessResourceFailureException("database unavailable");

        AgentBridgeException wrapped = AgentBridgeErrorMapper.asAgentBridgeException(
                databaseFailure,
                MvpErrorCode.INTERNAL_ERROR
        );

        assertEquals(MvpErrorCode.DATABASE_UNAVAILABLE, wrapped.getErrorCode());
        assertSame(databaseFailure, wrapped.getCause());
    }
}
