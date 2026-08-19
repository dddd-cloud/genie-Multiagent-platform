package com.jd.genie.platform.generation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.platform.marketplace.MarketplaceResourceService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DraftGenerationServiceTest {
    private final DraftGenerationService service = new DraftGenerationService(
        new ObjectMapper(), new MarketplaceResourceService(new ObjectMapper())
    );

    @Test
    void generatesAgentDraftFromNaturalLanguage() {
        GenerationDraftResponse response = service.generate(new GenerationDraftRequest("帮我创建一个分析 CSV 并生成 PDF 报告的 Agent", null));
        assertThat(response.target()).isEqualTo(GenerationTarget.AGENT);
        assertThat(response.name()).isEqualTo("数据分析师");
        assertThat(response.matchedResourceIds()).contains("agent-data-analyst");
        assertThat(response.draft().get("promptMode").asText()).isEqualTo("RAW");
        assertThat(response.draft().get("systemPrompt").asText()).isNotBlank();
        assertThat(response.draft().has("skillIds")).isTrue();
        assertThat(response.draft().has("skills")).isFalse();
        assertThat(response.draft().has("recommendedMarketplaceResources")).isFalse();
        assertThat(response.status()).isEqualTo("READY");
    }

    @Test
    void generatesTeamDraftWithoutInventingAgentIds() {
        GenerationDraftResponse response = service.generate(new GenerationDraftRequest("帮我创建一个研究报告协作 Team", null));
        assertThat(response.target()).isEqualTo(GenerationTarget.TEAM);
        assertThat(response.draft().get("masterAgentId").isNull()).isTrue();
        assertThat(response.draft().get("memberAgentIds")).isEmpty();
        assertThat(response.status()).isEqualTo("NEEDS_CONFIGURATION");
        assertThat(response.missingFields()).containsExactly("masterAgentId", "memberAgentIds");
    }

    @Test
    void rejectsBlankAndOverlongPrompts() {
        assertThatThrownBy(() -> service.generate(new GenerationDraftRequest(" ", GenerationTarget.AGENT)))
            .isInstanceOf(DraftGenerationService.GenerationValidationException.class);
        assertThatThrownBy(() -> service.generate(new GenerationDraftRequest("a".repeat(2001), GenerationTarget.AGENT)))
            .isInstanceOf(DraftGenerationService.GenerationValidationException.class);
    }
}
