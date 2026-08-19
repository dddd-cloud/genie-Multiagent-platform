package com.jd.genie.platform.settings.controller;

import com.jd.genie.platform.contract.ApiResponse;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.settings.service.UserSettingValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = UserSettingController.class)
public class UserSettingExceptionHandler {
    @ExceptionHandler({UserSettingValidationException.class, HttpMessageNotReadableException.class})
    ResponseEntity<ApiResponse<Void>> validation(Exception ignored) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ApiResponse<>(MvpErrorCode.VALIDATION_ERROR.name(), MvpErrorCode.VALIDATION_ERROR.name(), null));
    }
}
