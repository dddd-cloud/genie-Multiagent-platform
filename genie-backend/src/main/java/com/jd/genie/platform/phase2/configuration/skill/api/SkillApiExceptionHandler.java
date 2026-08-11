package com.jd.genie.platform.phase2.configuration.skill.api;

import com.jd.genie.platform.contract.ApiResponse;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2.configuration.skill.exception.SkillConfigurationException;
import com.jd.genie.platform.phase2contract.error.Phase2ContractException;
import com.jd.genie.platform.phase2contract.error.Phase2ErrorHttpStatus;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(basePackages = "com.jd.genie.platform.phase2.configuration.skill.api")
public class SkillApiExceptionHandler {

    @ExceptionHandler(SkillConfigurationException.class)
    ResponseEntity<ApiResponse<Void>> skill(SkillConfigurationException exception) {
        return error(safeCode(exception.code()));
    }

    @ExceptionHandler(Phase2ContractException.class)
    ResponseEntity<ApiResponse<Void>> phase2Contract(Phase2ContractException exception) {
        return error(safeCode(exception.errorCode()));
    }

    @ExceptionHandler({
        HttpMessageNotReadableException.class,
        HttpMediaTypeNotSupportedException.class,
        MethodArgumentTypeMismatchException.class,
        MissingServletRequestParameterException.class,
        IllegalArgumentException.class
    })
    ResponseEntity<ApiResponse<Void>> badRequest(Exception ignored) {
        return error(MvpErrorCode.VALIDATION_ERROR);
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ApiResponse<Void>> dataAccess(DataAccessException ignored) {
        return error(MvpErrorCode.DATABASE_UNAVAILABLE);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> unknown(Exception ignored) {
        return error(MvpErrorCode.INTERNAL_ERROR);
    }

    private ResponseEntity<ApiResponse<Void>> error(MvpErrorCode code) {
        HttpStatus status = HttpStatus.valueOf(Phase2ErrorHttpStatus.httpStatus(code));
        return ResponseEntity.status(status).body(new ApiResponse<>(code.name(), code.name(), null));
    }

    private MvpErrorCode safeCode(MvpErrorCode code) {
        return code == null ? MvpErrorCode.INTERNAL_ERROR : code;
    }
}
