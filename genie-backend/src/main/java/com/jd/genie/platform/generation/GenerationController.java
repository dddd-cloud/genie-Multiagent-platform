package com.jd.genie.platform.generation;

import com.jd.genie.platform.contract.ApiResponse;
import com.jd.genie.platform.contract.MvpErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/generation")
@RequiredArgsConstructor
public class GenerationController {
    private final DraftGenerationService generation;

    @PostMapping("/drafts")
    public ApiResponse<GenerationDraftResponse> draft(@RequestBody GenerationDraftRequest request) {
        return new ApiResponse<>("OK", "success", generation.generate(request));
    }

    @ExceptionHandler(DraftGenerationService.GenerationValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> validation(DraftGenerationService.GenerationValidationException exception) {
        return new ApiResponse<>(MvpErrorCode.VALIDATION_ERROR.name(), exception.getMessage(), null);
    }
}
