package com.jd.genie.platform.phase2.configuration.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.platform.contract.ApiResponse;
import com.jd.genie.platform.contract.CurrentUserProvider;
import com.jd.genie.platform.phase2.configuration.model.ModelCatalogItem;
import com.jd.genie.platform.phase2.configuration.model.ModelCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
}
