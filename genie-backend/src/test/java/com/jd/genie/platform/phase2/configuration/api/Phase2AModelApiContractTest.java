package com.jd.genie.platform.phase2.configuration.api;

import com.jd.genie.platform.phase2.configuration.model.ModelCatalogItem;
import com.jd.genie.platform.phase2.configuration.model.ModelCatalogService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class Phase2AModelApiContractTest extends Phase2AApiTestSupport {

    @Test
    void listsOnlySafeModelCatalogFieldsInStableEnvelope() throws Exception {
        ModelCatalogService service = mock(ModelCatalogService.class);
        when(service.listModels()).thenReturn(List.of(
            new ModelCatalogItem("system-default", "system-default", true, true),
            new ModelCatalogItem("qwen-plus", "qwen-plus", false, true)
        ));
        var mvc = mvc(new Phase2ModelController(service, currentUserProvider));

        mvc.perform(get("/api/v2/models"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.data[0].name").value("system-default"))
            .andExpect(jsonPath("$.data[0].isDefault").value(true))
            .andExpect(jsonPath("$.data[1].available").value(true))
            .andExpect(jsonPath("$.data[0].apiKey").doesNotExist())
            .andExpect(jsonPath("$.data[0].baseUrl").doesNotExist())
            .andExpect(jsonPath("$.data[0].headers").doesNotExist())
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("SECRET"))));
    }
}
