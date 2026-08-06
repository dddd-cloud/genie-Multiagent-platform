package com.jd.genie.platform.phase2.runtime.controller;

import com.jd.genie.platform.agentbridge.AgentBridgeException;
import com.jd.genie.platform.contract.ApiResponse;
import com.jd.genie.platform.contract.MvpErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {Phase2GptController.class, Phase2AgentTestController.class})
public final class Phase2GptExceptionHandler {
    @ExceptionHandler(AgentBridgeException.class)
    public ResponseEntity<ApiResponse<Void>> handle(AgentBridgeException exception) {
        MvpErrorCode code = exception.getErrorCode();
        return ResponseEntity.status(status(code))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ApiResponse<>(code.name(), exception.getMessage(), null));
    }

    private HttpStatus status(MvpErrorCode code) {
        return switch (code) {
            case VALIDATION_ERROR, LOCAL_CONTEXT_INVALID -> HttpStatus.BAD_REQUEST;
            case LOCAL_CONTEXT_TOO_LARGE -> HttpStatus.PAYLOAD_TOO_LARGE;
            case AUTH_REQUIRED -> HttpStatus.UNAUTHORIZED;
            case RESOURCE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case NO_SUITABLE_AGENT, AGENT_OFFLINE -> HttpStatus.CONFLICT;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
