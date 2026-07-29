package com.jd.genie.platform.conversation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.conversation.exception.ConversationException;
import com.jd.genie.platform.conversation.snapshot.SnapshotValidator;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SnapshotValidatorTest {

    @Test
    void acceptsValidObjectWithPayloadVersionOne() {
        validator(1024).validate("{\"payloadVersion\":1,\"events\":[]}", 1);
    }

    @Test
    void rejectsInvalidJsonArrayNullMissingStringAndWrongVersions() {
        assertSnapshotError(MvpErrorCode.SNAPSHOT_INVALID, () -> validator(1024).validate("not-json", 1));
        assertSnapshotError(MvpErrorCode.SNAPSHOT_INVALID, () -> validator(1024).validate("[]", 1));
        assertSnapshotError(MvpErrorCode.SNAPSHOT_INVALID, () -> validator(1024).validate("null", 1));
        assertSnapshotError(MvpErrorCode.SNAPSHOT_INVALID, () -> validator(1024).validate("{\"events\":[]}", 1));
        assertSnapshotError(MvpErrorCode.SNAPSHOT_INVALID, () -> validator(1024).validate("{\"payloadVersion\":\"1\"}", 1));
        assertSnapshotError(MvpErrorCode.SNAPSHOT_INVALID, () -> validator(1024).validate("{\"payloadVersion\":2}", 1));
        assertSnapshotError(MvpErrorCode.SNAPSHOT_INVALID, () -> validator(1024).validate("{\"payloadVersion\":1}", 2));
    }

    @Test
    void countsUtf8BytesForLimit() {
        String exact = snapshotWithUtf8Payload(80);
        assertEquals(80, exact.getBytes(StandardCharsets.UTF_8).length);
        validator(80).validate(exact, 1);

        String over = exact.replace("\"}", "a\"}");
        assertEquals(81, over.getBytes(StandardCharsets.UTF_8).length);
        assertSnapshotError(MvpErrorCode.SNAPSHOT_TOO_LARGE, () -> validator(80).validate(over, 1));
    }

    @Test
    void allowsNonSemanticEventShape() {
        validator(1024).validate("{\"payloadVersion\":1,\"events\":\"not-validated\",\"messageType\":123}", 1);
    }

    private SnapshotValidator validator(int maxBytes) {
        return new SnapshotValidator(new ObjectMapper(), maxBytes);
    }

    private String snapshotWithUtf8Payload(int targetBytes) {
        String prefix = "{\"payloadVersion\":1,\"text\":\"中文🙂";
        String suffix = "\"}";
        int baseBytes = (prefix + suffix).getBytes(StandardCharsets.UTF_8).length;
        int fillerBytes = targetBytes - baseBytes;
        if (fillerBytes < 0) {
            throw new IllegalArgumentException("targetBytes too small");
        }
        return prefix + "a".repeat(fillerBytes) + suffix;
    }

    private void assertSnapshotError(MvpErrorCode expectedCode, ThrowingRunnable runnable) {
        ConversationException exception = assertThrows(ConversationException.class, runnable::run);
        assertEquals(expectedCode, exception.code());
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run();
    }
}
