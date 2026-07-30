package com.jd.genie.platform.agentbridge;

import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.conversation.exception.ConversationException;
import org.springframework.dao.DataAccessException;

public final class AgentBridgeErrorMapper {
    private static final int MAX_ERROR_MESSAGE_LENGTH = 1_000;

    private AgentBridgeErrorMapper() {
    }

    public static MvpErrorCode errorCode(Throwable error, MvpErrorCode fallbackCode) {
        if (error instanceof AgentBridgeException bridgeException) {
            return bridgeException.getErrorCode();
        }
        if (error instanceof ConversationException conversationException && conversationException.code() != null) {
            return conversationException.code();
        }
        if (error instanceof DataAccessException) {
            return MvpErrorCode.DATABASE_UNAVAILABLE;
        }
        return fallbackCode;
    }

    public static AgentBridgeException asAgentBridgeException(
            Throwable error,
            MvpErrorCode fallbackCode
    ) {
        if (error instanceof AgentBridgeException bridgeException) {
            return bridgeException;
        }
        MvpErrorCode code = errorCode(error, fallbackCode);
        String message = message(error, code);
        return new AgentBridgeException(code, message, error);
    }

    public static String message(Throwable error, MvpErrorCode code) {
        String original = error == null ? null : error.getMessage();
        String safe = original == null || original.isBlank() ? code.name() : original;
        return safe.length() <= MAX_ERROR_MESSAGE_LENGTH
                ? safe
                : safe.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }
}
