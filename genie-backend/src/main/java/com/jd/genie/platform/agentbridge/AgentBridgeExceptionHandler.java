package com.jd.genie.platform.agentbridge;

import com.jd.genie.controller.GenieController;
import com.jd.genie.platform.contract.ApiResponse;
import com.jd.genie.platform.contract.MvpErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = GenieController.class)
public final class AgentBridgeExceptionHandler {
    @ExceptionHandler(AgentBridgeException.class)
    public ResponseEntity<ApiResponse<Void>> handle(AgentBridgeException exception) {
        MvpErrorCode responseCode = responseCode(exception.getErrorCode());
        return ResponseEntity.status(status(responseCode))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ApiResponse<>(responseCode.name(), exception.getMessage(), null));
    }

    private MvpErrorCode responseCode(MvpErrorCode errorCode) {
        return switch (errorCode) {
            case VALIDATION_ERROR,
                    AUTH_REQUIRED,
                    RESOURCE_NOT_FOUND,
                    CONVERSATION_BUSY,
                    DUPLICATE_REQUEST,
                    MESSAGE_STATE_CONFLICT,
                    SNAPSHOT_TOO_LARGE,
                    DATABASE_UNAVAILABLE -> errorCode;
            default -> MvpErrorCode.INTERNAL_ERROR;
        };
    }

    private HttpStatus status(MvpErrorCode errorCode) {
        return switch (errorCode) {
            case VALIDATION_ERROR -> HttpStatus.BAD_REQUEST;
            case AUTH_REQUIRED -> HttpStatus.UNAUTHORIZED;
            case RESOURCE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONVERSATION_BUSY, DUPLICATE_REQUEST, MESSAGE_STATE_CONFLICT -> HttpStatus.CONFLICT;
            case SNAPSHOT_TOO_LARGE -> HttpStatus.PAYLOAD_TOO_LARGE;
            case DATABASE_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
