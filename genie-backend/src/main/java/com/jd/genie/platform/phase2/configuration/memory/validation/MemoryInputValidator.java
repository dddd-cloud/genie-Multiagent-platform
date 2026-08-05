package com.jd.genie.platform.phase2.configuration.memory.validation;

import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2.configuration.memory.dto.ConversationSummaryAnalysisRequest;
import com.jd.genie.platform.phase2.configuration.memory.dto.ConversationSummaryTurn;
import com.jd.genie.platform.phase2.configuration.memory.dto.MemoryAnalysisRequest;
import com.jd.genie.platform.phase2.configuration.memory.exception.MemoryAnalysisException;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class MemoryInputValidator {
    public static final int MAX_USER_MESSAGE_CODE_POINTS = 20_000;
    public static final int MAX_ASSISTANT_MESSAGE_CODE_POINTS = 20_000;
    public static final int MAX_LONG_TERM_MEMORY_CODE_POINTS = 12_000;
    public static final int MAX_CURRENT_SUMMARY_CODE_POINTS = 20_000;
    public static final int MAX_SUMMARY_TURNS = 20;
    public static final int MAX_TOTAL_REQUEST_CODE_POINTS = 30_000;

    private static final Set<String> TURN_STATUSES = Set.of("COMPLETED", "FAILED", "INTERRUPTED", "STREAMING", "PENDING");

    public void validateMemoryRequest(MemoryAnalysisRequest request) {
        if (request == null || blank(request.conversationId()) || blank(request.userMessage()) || blank(request.turnStatus())) {
            throw validation();
        }
        if (!TURN_STATUSES.contains(request.turnStatus())) {
            throw validation();
        }
        checkLength(request.userMessage(), MAX_USER_MESSAGE_CODE_POINTS);
        checkLength(request.assistantMessage(), MAX_ASSISTANT_MESSAGE_CODE_POINTS);
        checkLength(request.currentLongTermMemory(), MAX_LONG_TERM_MEMORY_CODE_POINTS);
        checkTotal(request.userMessage(), request.assistantMessage(), request.currentLongTermMemory());
    }

    public void validateSummaryRequest(ConversationSummaryAnalysisRequest request) {
        if (request == null || blank(request.conversationId())) {
            throw validation();
        }
        checkLength(request.currentSummary(), MAX_CURRENT_SUMMARY_CODE_POINTS);
        if (request.newTurns().size() > MAX_SUMMARY_TURNS) {
            throw validation();
        }
        int total = codePoints(request.currentSummary());
        for (ConversationSummaryTurn turn : request.newTurns()) {
            if (turn == null || turn.turnNo() == null || blank(turn.userMessage()) || blank(turn.assistantMessage())) {
                throw validation();
            }
            if (!"COMPLETED".equals(turn.assistantStatus())) {
                throw validation();
            }
            checkLength(turn.userMessage(), MAX_USER_MESSAGE_CODE_POINTS);
            checkLength(turn.assistantMessage(), MAX_ASSISTANT_MESSAGE_CODE_POINTS);
            total += codePoints(turn.userMessage()) + codePoints(turn.assistantMessage());
        }
        if (total > MAX_TOTAL_REQUEST_CODE_POINTS) {
            throw validation();
        }
    }

    private void checkTotal(String... values) {
        int total = 0;
        for (String value : values) {
            total += codePoints(value);
        }
        if (total > MAX_TOTAL_REQUEST_CODE_POINTS) {
            throw validation();
        }
    }

    private void checkLength(String value, int maxCodePoints) {
        if (codePoints(value) > maxCodePoints) {
            throw validation();
        }
    }

    private int codePoints(String value) {
        return value == null ? 0 : value.codePointCount(0, value.length());
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private MemoryAnalysisException validation() {
        return new MemoryAnalysisException(MvpErrorCode.VALIDATION_ERROR, MvpErrorCode.VALIDATION_ERROR.name());
    }
}
