package com.jd.genie.platform.phase2.configuration.memory;

import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2.configuration.memory.dto.ConversationSummaryAnalysisRequest;
import com.jd.genie.platform.phase2.configuration.memory.dto.ConversationSummaryTurn;
import com.jd.genie.platform.phase2.configuration.memory.dto.MemoryAnalysisRequest;
import com.jd.genie.platform.phase2.configuration.memory.exception.MemoryAnalysisException;
import com.jd.genie.platform.phase2.configuration.memory.validation.MemoryInputValidator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MemoryInputValidatorTest {
    private final MemoryInputValidator validator = new MemoryInputValidator();

    @Test
    void acceptsBoundarySizedMemoryAndSummaryInputs() {
        assertDoesNotThrow(() -> validator.validateMemoryRequest(
            new MemoryAnalysisRequest("c1", repeat("a", 17_999), repeat("b", 1), repeat("c", 12_000), "COMPLETED")
        ));
        assertDoesNotThrow(() -> validator.validateSummaryRequest(
            new ConversationSummaryAnalysisRequest("c1", repeat("s", 100), List.of(
                new ConversationSummaryTurn(1L, repeat("u", 100), repeat("a", 100), "COMPLETED")
            ))
        ));
    }

    @Test
    void rejectsOversizedUnknownStatusAndTooManySummaryTurns() {
        assertValidation(() -> validator.validateMemoryRequest(
            new MemoryAnalysisRequest("c1", repeat("a", 20_001), "", "", "COMPLETED")
        ));
        assertValidation(() -> validator.validateMemoryRequest(
            new MemoryAnalysisRequest("c1", "Q", "A", "", "DONE")
        ));
        assertValidation(() -> validator.validateSummaryRequest(
            new ConversationSummaryAnalysisRequest("c1", "", java.util.stream.IntStream.rangeClosed(1, 21)
                .mapToObj(i -> new ConversationSummaryTurn((long) i, "Q", "A", "COMPLETED")).toList())
        ));
    }

    private void assertValidation(Executable executable) {
        MemoryAnalysisException ex = assertThrows(MemoryAnalysisException.class, executable::run);
        assertEquals(MvpErrorCode.VALIDATION_ERROR, ex.code());
    }

    private String repeat(String value, int count) {
        return value.repeat(count);
    }

    @FunctionalInterface
    private interface Executable {
        void run();
    }
}
