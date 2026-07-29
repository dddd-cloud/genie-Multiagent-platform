package com.jd.genie.platform.user.controller;

import com.jd.genie.platform.contract.ApiResponse;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.user.service.UserAlreadyExistsException;
import com.jd.genie.platform.user.service.UserNotFoundException;
import com.jd.genie.platform.user.service.UserValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = AdminUserController.class)
public class AdminUserExceptionHandler {
    @ExceptionHandler({UserValidationException.class, HttpMessageNotReadableException.class})
    ResponseEntity<ApiResponse<Void>> validation(Exception ignored) { return response(HttpStatus.BAD_REQUEST, MvpErrorCode.VALIDATION_ERROR); }
    @ExceptionHandler(UserAlreadyExistsException.class)
    ResponseEntity<ApiResponse<Void>> duplicate(UserAlreadyExistsException ignored) { return response(HttpStatus.CONFLICT, MvpErrorCode.USER_ALREADY_EXISTS); }
    @ExceptionHandler(UserNotFoundException.class)
    ResponseEntity<ApiResponse<Void>> missing(UserNotFoundException ignored) { return response(HttpStatus.NOT_FOUND, MvpErrorCode.RESOURCE_NOT_FOUND); }
    private ResponseEntity<ApiResponse<Void>> response(HttpStatus status, MvpErrorCode code) {
        return ResponseEntity.status(status).body(new ApiResponse<>(code.name(), code.name(), null));
    }
}
