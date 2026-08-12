package com.jd.genie.platform.phase2.skillruntime.execution;

import com.jd.genie.platform.contract.ApiResponse;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2contract.error.Phase2ContractException;
import com.jd.genie.platform.phase2contract.error.Phase2ErrorHttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = BrowserSkillExecutionController.class)
public class BrowserSkillExecutionExceptionHandler {
    @ExceptionHandler(Phase2ContractException.class)
    ResponseEntity<ApiResponse<Void>> handle(Phase2ContractException e) {
        MvpErrorCode code=e.errorCode();
        return ResponseEntity.status(Phase2ErrorHttpStatus.httpStatus(code)).body(new ApiResponse<>(code.name(), code.name(), null));
    }
}
