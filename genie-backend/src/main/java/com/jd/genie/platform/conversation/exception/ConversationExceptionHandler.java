package com.jd.genie.platform.conversation.exception;

import com.jd.genie.platform.contract.ApiResponse;
import com.jd.genie.platform.contract.MvpErrorCode;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice(basePackages = "com.jd.genie.platform.conversation")
public class ConversationExceptionHandler {

    @ExceptionHandler(ConversationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConversationException(ConversationException exception) {
        MvpErrorCode code = exception.code() == null ? MvpErrorCode.INTERNAL_ERROR : exception.code();
        return error(status(code), code, exception.getMessage());
    }

    @ExceptionHandler({
        MethodArgumentTypeMismatchException.class,
        MissingServletRequestParameterException.class,
        MissingServletRequestPartException.class,
        MaxUploadSizeExceededException.class,
        MultipartException.class,
        HttpMessageNotReadableException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception exception) {
        if (exception instanceof MaxUploadSizeExceededException) {
            return error(HttpStatus.BAD_REQUEST, MvpErrorCode.VALIDATION_ERROR, "file is too large");
        }
        return error(HttpStatus.BAD_REQUEST, MvpErrorCode.VALIDATION_ERROR, "Invalid request");
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataAccessException(DataAccessException exception) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, MvpErrorCode.DATABASE_UNAVAILABLE, "Database unavailable");
    }

    private ResponseEntity<ApiResponse<Void>> error(HttpStatus status, MvpErrorCode code, String message) {
        String responseMessage = message == null || message.isBlank() ? "Internal error" : message;
        return ResponseEntity.status(status)
            .contentType(MediaType.APPLICATION_JSON)
            .body(new ApiResponse<>(code.name(), responseMessage, null));
    }

    private HttpStatus status(MvpErrorCode code) {
        return switch (code) {
            case VALIDATION_ERROR -> HttpStatus.BAD_REQUEST;
            case AUTH_REQUIRED -> HttpStatus.UNAUTHORIZED;
            case RESOURCE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONVERSATION_BUSY, DUPLICATE_REQUEST, MESSAGE_STATE_CONFLICT -> HttpStatus.CONFLICT;
            case SNAPSHOT_TOO_LARGE -> HttpStatus.PAYLOAD_TOO_LARGE;
            case DATABASE_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}