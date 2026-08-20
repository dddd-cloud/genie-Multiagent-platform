package com.jd.genie.platform.phase2.configuration.model.api;

import com.jd.genie.platform.contract.ApiResponse;
import com.jd.genie.platform.contract.CurrentUserProvider;
import com.jd.genie.platform.phase2.configuration.model.LlmModelWriteRequest;
import com.jd.genie.platform.phase2.configuration.model.ModelCatalogItem;
import com.jd.genie.platform.phase2.configuration.model.ModelCatalogService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/models")
@RequiredArgsConstructor
public class Phase2ModelController {
    private final ModelCatalogService modelCatalogService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    public ApiResponse<List<ModelCatalogItem>> list() {
        currentUserProvider.requireCurrentUser();
        return new ApiResponse<>("OK", "success", List.copyOf(modelCatalogService.listModels()));
    }

    @GetMapping("/{id}")
    public ApiResponse<ModelCatalogItem> get(@PathVariable("id") String id) {
        currentUserProvider.requireCurrentUser();
        return new ApiResponse<>("OK", "success", modelCatalogService.getModel(id));
    }

    @PostMapping
    public ApiResponse<ModelCatalogItem> create(@RequestBody LlmModelWriteRequest request) {
        currentUserProvider.requireCurrentUser();
        return new ApiResponse<>("OK", "success", modelCatalogService.createModel(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<ModelCatalogItem> update(
        @PathVariable("id") String id,
        @RequestBody LlmModelWriteRequest request
    ) {
        currentUserProvider.requireCurrentUser();
        return new ApiResponse<>("OK", "success", modelCatalogService.updateModel(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable("id") String id) {
        currentUserProvider.requireCurrentUser();
        modelCatalogService.deleteModel(id);
        return new ApiResponse<>("OK", "success", null);
    }
}
