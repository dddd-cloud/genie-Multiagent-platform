package com.jd.genie.platform.usage.controller;

import com.jd.genie.platform.contract.ApiResponse;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.usage.service.UsageValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {AdminUsageController.class, MyUsageController.class})
public class UsageExceptionHandler {
    @ExceptionHandler(UsageValidationException.class)
    ResponseEntity<ApiResponse<Void>> validation(UsageValidationException ignored) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ApiResponse<>(MvpErrorCode.VALIDATION_ERROR.name(), MvpErrorCode.VALIDATION_ERROR.name(), null));
    }
}
