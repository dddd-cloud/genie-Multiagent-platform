package com.jd.genie.platform.phase2.tooling;

import com.jd.genie.platform.contract.ApiResponse;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2contract.error.Phase2ContractException;
import com.jd.genie.platform.phase2contract.error.Phase2ErrorHttpStatus;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = McpServerController.class)
public class McpExceptionHandler {
    @ExceptionHandler(Phase2ContractException.class)
    ResponseEntity<ApiResponse<Void>> phase2(Phase2ContractException ex) {
        MvpErrorCode code=ex.errorCode(); return ResponseEntity.status(Phase2ErrorHttpStatus.httpStatus(code)).body(new ApiResponse<>(code.name(),code.name(),null));
    }
    @ExceptionHandler({HttpMessageNotReadableException.class, IllegalArgumentException.class})
    ResponseEntity<ApiResponse<Void>> validation(Exception ex) { return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse<>(MvpErrorCode.VALIDATION_ERROR.name(),MvpErrorCode.VALIDATION_ERROR.name(),null)); }
}
