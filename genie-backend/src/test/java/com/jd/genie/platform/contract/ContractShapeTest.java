package com.jd.genie.platform.contract;

import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContractShapeTest {

    private static final String PACKAGE = "com.jd.genie.platform.contract";

    private static final List<String> EXPECTED_TYPES = List.of(
        "ApiResponse",
        "PageResponse",
        "UserRole",
        "ConversationMessageRole",
        "ConversationMessageStatus",
        "CurrentUser",
        "CurrentUserProvider",
        "ConversationExecutionCommand",
        "ConversationExecutionResult",
        "ConversationHistoryItem",
        "MessageCompletionCommand",
        "MessageFailureCommand",
        "ConversationExecutionPort",
        "StreamSnapshotEnvelope",
        "MvpErrorCode"
    );

    @Test
    void allFifteenContractTypesExist() throws ClassNotFoundException {
        for (String typeName : EXPECTED_TYPES) {
            Class<?> type = Class.forName(PACKAGE + "." + typeName);
            assertNotNull(type, "Missing contract type: " + typeName);
        }
        assertEquals(15, EXPECTED_TYPES.size());
    }

    @Test
    void apiResponseComponents() {
        assertRecordComponents(ApiResponse.class, "code", "message", "data");
    }

    @Test
    void pageResponseComponents() {
        assertRecordComponents(PageResponse.class, "items", "page", "pageSize", "hasMore");
        assertFalse(hasField(PageResponse.class, "total"));
    }

    @Test
    void currentUserComponents() {
        assertRecordComponents(CurrentUser.class,
            "tenantId", "userId", "username", "displayName", "role");
    }

    @Test
    void conversationExecutionCommandComponents() {
        assertRecordComponents(ConversationExecutionCommand.class,
            "conversationId", "requestId", "query", "deepThink", "outputStyle");
    }

    @Test
    void conversationExecutionResultComponents() {
        assertRecordComponents(ConversationExecutionResult.class,
            "conversationId", "requestId", "userMessageId", "assistantMessageId", "turnNo");
    }

    @Test
    void conversationHistoryItemComponents() {
        assertRecordComponents(ConversationHistoryItem.class, "turnNo", "role", "content");
    }

    @Test
    void messageCompletionCommandComponents() {
        assertRecordComponents(MessageCompletionCommand.class,
            "assistantMessageId", "finalContent", "snapshotJson", "payloadVersion");
    }

    @Test
    void messageFailureCommandComponents() {
        assertRecordComponents(MessageFailureCommand.class,
            "assistantMessageId", "errorCode", "errorMessage", "partialSnapshotJson", "payloadVersion");
    }

    @Test
    void streamSnapshotEnvelopeComponents() {
        RecordComponent[] components = StreamSnapshotEnvelope.class.getRecordComponents();
        assertRecordComponents(StreamSnapshotEnvelope.class,
            "payloadVersion", "truncated", "events");
        assertEquals(
            "java.util.List<com.jd.genie.model.response.GptProcessResult>",
            components[2].getGenericType().getTypeName()
        );
    }

    @Test
    void userRoleValues() {
        assertArrayEquals(new String[]{"ADMIN", "USER"},
            enumNames(UserRole.class));
    }

    @Test
    void conversationMessageRoleValues() {
        assertArrayEquals(new String[]{"USER", "ASSISTANT"},
            enumNames(ConversationMessageRole.class));
    }

    @Test
    void conversationMessageStatusValues() {
        assertArrayEquals(new String[]{
            "PENDING", "STREAMING", "COMPLETED", "FAILED", "INTERRUPTED"
        }, enumNames(ConversationMessageStatus.class));
    }

    @Test
    void mvpErrorCodeValues() {
        assertArrayEquals(new String[]{
            "VALIDATION_ERROR",
            "AUTH_REQUIRED",
            "AUTH_INVALID_CREDENTIALS",
            "INTERNAL_TOKEN_INVALID",
            "ACCESS_DENIED",
            "CSRF_INVALID",
            "RESOURCE_NOT_FOUND",
            "USER_ALREADY_EXISTS",
            "CONVERSATION_BUSY",
            "DUPLICATE_REQUEST",
            "MESSAGE_STATE_CONFLICT",
            "SNAPSHOT_TOO_LARGE",
            "AGENT_DOWNSTREAM_ERROR",
            "AGENT_NO_FINAL_EVENT",
            "INTERNAL_ERROR",
            "DATABASE_UNAVAILABLE",
            "CLIENT_DISCONNECTED",
            "SERVICE_RESTARTED",
            "AGENT_STREAM_INTERRUPTED",
            "SNAPSHOT_INVALID"
        }, enumNames(MvpErrorCode.class));
    }

    @Test
    void conversationExecutionPortMethods() throws NoSuchMethodException {
        assertEquals(ConversationExecutionResult.class,
            ConversationExecutionPort.class.getMethod("prepareExecution",
                CurrentUser.class, ConversationExecutionCommand.class).getReturnType());
        assertEquals(void.class,
            ConversationExecutionPort.class.getMethod("markStreaming",
                CurrentUser.class, String.class).getReturnType());
        assertEquals(void.class,
            ConversationExecutionPort.class.getMethod("complete",
                CurrentUser.class, MessageCompletionCommand.class).getReturnType());
        assertEquals(void.class,
            ConversationExecutionPort.class.getMethod("fail",
                CurrentUser.class, MessageFailureCommand.class).getReturnType());
        assertEquals(void.class,
            ConversationExecutionPort.class.getMethod("interrupt",
                CurrentUser.class, MessageFailureCommand.class).getReturnType());
        assertEquals(List.class,
            ConversationExecutionPort.class.getMethod("loadCompletedHistory",
                CurrentUser.class, String.class, String.class, int.class, int.class).getReturnType());
    }

    @Test
    void contractTypesHaveNoSpringAnnotations() throws ClassNotFoundException {
        for (String typeName : EXPECTED_TYPES) {
            Class<?> type = Class.forName(PACKAGE + "." + typeName);
            assertFalse(type.isAnnotationPresent(Component.class),
                type.getSimpleName() + " must not have Spring annotations");
        }
    }

    private static void assertRecordComponents(Class<?> recordClass, String... expected) {
        RecordComponent[] components = recordClass.getRecordComponents();
        String[] actual = Arrays.stream(components)
            .map(RecordComponent::getName)
            .toArray(String[]::new);
        assertArrayEquals(expected, actual);
    }

    private static String[] enumNames(Class<? extends Enum<?>> enumClass) {
        return Arrays.stream(enumClass.getEnumConstants())
            .map(Enum::name)
            .toArray(String[]::new);
    }

    private static boolean hasField(Class<?> clazz, String fieldName) {
        return Arrays.stream(clazz.getRecordComponents())
            .anyMatch(c -> c.getName().equals(fieldName));
    }
}
