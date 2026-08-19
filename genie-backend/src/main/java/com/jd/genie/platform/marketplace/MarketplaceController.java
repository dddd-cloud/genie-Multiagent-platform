package com.jd.genie.platform.marketplace;

import com.jd.genie.platform.contract.ApiResponse;
import com.jd.genie.platform.contract.CurrentUserProvider;
import com.jd.genie.platform.contract.MvpErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

@RestController
@RequestMapping("/api/v2/marketplace")
@RequiredArgsConstructor
public class MarketplaceController {
    private final MarketplaceResourceService resources;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/resources")
    public ApiResponse<List<MarketplaceResourceView>> resources(
        @RequestParam(required = false) MarketplaceResourceType type,
        @RequestParam(required = false) String category,
        @RequestParam(required = false) String q
    ) {
        return success(resources.search(type, category, q));
    }

    @GetMapping("/categories")
    public ApiResponse<List<String>> categories() {
        return success(resources.categories());
    }

    @GetMapping("/resources/{id}")
    public ApiResponse<MarketplaceResourceView> resource(@PathVariable String id) {
        return success(resources.get(id));
    }

    @PostMapping("/resources/{id}/draft")
    public ApiResponse<MarketplaceDraftResponse> draft(@PathVariable String id) {
        return success(resources.createDraft(currentUserProvider.requireCurrentUser(), id));
    }

    @ExceptionHandler(MarketplaceResourceService.MarketplaceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> notFound() {
        return new ApiResponse<>(MvpErrorCode.RESOURCE_NOT_FOUND.name(), "Marketplace resource not found", null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> typeMismatch() {
        return new ApiResponse<>(MvpErrorCode.VALIDATION_ERROR.name(), "Invalid request parameter", null);
    }

    private <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>("OK", "success", data);
    }
}
