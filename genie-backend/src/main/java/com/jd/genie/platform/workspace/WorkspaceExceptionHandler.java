package com.jd.genie.platform.workspace;

import com.jd.genie.platform.contract.ApiResponse;
import com.jd.genie.platform.contract.MvpErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.jd.genie.platform.workspace")
public class WorkspaceExceptionHandler {

    @ExceptionHandler(WorkspaceException.class)
    public ResponseEntity<ApiResponse<Void>> handle(WorkspaceException exception) {
        MvpErrorCode code = exception.code() == null ? MvpErrorCode.INTERNAL_ERROR : exception.code();
        return ResponseEntity.status(status(code))
            .contentType(MediaType.APPLICATION_JSON)
            .body(new ApiResponse<>(code.name(), exception.getMessage(), null));
    }

    private HttpStatus status(MvpErrorCode code) {
        return switch (code) {
            case VALIDATION_ERROR -> HttpStatus.BAD_REQUEST;
            case AUTH_REQUIRED -> HttpStatus.UNAUTHORIZED;
            case ACCESS_DENIED -> HttpStatus.FORBIDDEN;
            case RESOURCE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case SNAPSHOT_TOO_LARGE -> HttpStatus.PAYLOAD_TOO_LARGE;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
