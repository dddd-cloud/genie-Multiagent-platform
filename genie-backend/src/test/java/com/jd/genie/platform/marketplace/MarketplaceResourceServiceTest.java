package com.jd.genie.platform.marketplace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.UserRole;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MarketplaceResourceServiceTest {
    private final MarketplaceResourceService service = new MarketplaceResourceService(new ObjectMapper());
    private static final CurrentUser USER = new CurrentUser("tenant-1", "user-1", "alice", "Alice", UserRole.USER);

    @Test
    void catalogContainsAllFourResourceKindsWithoutCredentials() {
        assertThat(service.entries()).hasSize(58);
        assertThat(service.entries()).extracting(MarketplaceCatalogEntry::type)
            .filteredOn(MarketplaceResourceType.AGENT::equals).hasSize(33);
        assertThat(service.entries()).extracting(MarketplaceCatalogEntry::type)
            .filteredOn(MarketplaceResourceType.TEAM::equals).hasSize(12);
        assertThat(service.entries()).extracting(MarketplaceCatalogEntry::type)
            .filteredOn(MarketplaceResourceType.SKILL::equals).hasSize(8);
        assertThat(service.entries()).extracting(MarketplaceCatalogEntry::type)
            .filteredOn(MarketplaceResourceType.MCP::equals).hasSize(5);
        assertThat(service.entries()).allSatisfy(entry -> {
            assertThat(entry.draft().toString()).doesNotContainIgnoringCase("credential_envelope");
            assertThat(entry.draft().toString()).doesNotContain("apiKey");
            assertThat(entry.draft().toString()).doesNotContain("tenantId");
        });
    }

    @Test
    void curatedExpertTeamsOnlyReferenceListedExpertBlueprints() {
        var expertIds = service.search(MarketplaceResourceType.AGENT, null, null).stream()
            .map(MarketplaceResourceView::id)
            .collect(java.util.stream.Collectors.toSet());
        var teamBlueprintIds = service.entries().stream()
            .filter(entry -> entry.type() == MarketplaceResourceType.TEAM)
            .flatMap(entry -> {
                var templates = entry.draft().path("recommendedAgentTemplates");
                return java.util.stream.StreamSupport.stream(templates.spliterator(), false)
                    .map(node -> node.asText());
            })
            .toList();

        assertThat(teamBlueprintIds).isNotEmpty();
        assertThat(expertIds).containsAll(teamBlueprintIds);
    }

    @Test
    void searchFiltersByTypeAndQuery() {
        assertThat(service.search(MarketplaceResourceType.SKILL, null, "pyodide"))
            .extracting(MarketplaceResourceView::id)
            .containsExactlyInAnyOrder("skill-browser-python-report", "skill-jupyterlite-notebook",
                "skill-csv-summary", "skill-create-pdf-report");
        assertThat(service.search(null, null, "github"))
            .extracting(MarketplaceResourceView::id)
            .containsExactly("mcp-deepwiki-readonly", "mcp-github-public-tools");
    }

    @Test
    void draftIsCopiedAndDoesNotExposeCatalogMutation() {
        MarketplaceDraftResponse first = service.createDraft(USER, "agent-data-analyst");
        assertThat(first.status()).isEqualTo("READY");
        assertThat(first.ownerUserId()).isEqualTo("user-1");
        assertThat(first.missingFields()).isEmpty();
        ((ObjectNode) first.draft()).put("name", "changed locally");
        assertThat(service.createDraft(USER, "agent-data-analyst").draft().get("name").asText())
            .isEqualTo("数据分析师");
    }

    @Test
    void incompleteTemplatesReportRequiredConfiguration() {
        MarketplaceDraftResponse team = service.createDraft(USER, "team-research-report");
        assertThat(team.status()).isEqualTo("NEEDS_CONFIGURATION");
        assertThat(team.missingFields()).containsExactly("masterAgentId", "memberAgentIds");

        MarketplaceDraftResponse github = service.createDraft(USER, "mcp-github-public-tools");
        assertThat(github.status()).isEqualTo("NEEDS_CONFIGURATION");
        assertThat(github.missingFields()).containsExactly("授权凭据（需在 MCP 设置中填写）");
        assertThat(service.get("mcp-himalayas-remote-jobs").installMode()).isEqualTo("INSTALL");
        assertThat(service.get("mcp-deepwiki-readonly").installMode()).isEqualTo("CONFIGURE");
        assertThat(service.get("mcp-context7-docs").installMode()).isEqualTo("CONFIGURE");
    }

    @Test
    void aBrokenCatalogEntryDoesNotPreventStartupAndIsDropped() {
        String json = """
            [
              {
                "id": "ok-agent",
                "type": "AGENT",
                "slug": "ok-agent",
                "name": "OK",
                "tagline": "ok",
                "description": "ok",
                "category": "分析",
                "tags": [],
                "sourceType": "internal",
                "sourceUrl": "",
                "license": "internal",
                "trustTier": "internal",
                "capabilities": [],
                "setup": [],
                "draft": { "systemPrompt": "hello", "promptMode": "RAW" }
              },
              {
                "id": "bad-agent",
                "type": "AGENT",
                "slug": "bad-agent",
                "name": "Bad",
                "tagline": "bad",
                "description": "bad",
                "category": "分析",
                "tags": [],
                "sourceType": "internal",
                "sourceUrl": "",
                "license": "internal",
                "trustTier": "internal",
                "capabilities": [],
                "setup": [],
                "draft": { "promptMode": "UNKNOWN" }
              }
            ]
            """;
        MarketplaceResourceService tolerant = new MarketplaceResourceService(new ObjectMapper(), json);
        assertThat(tolerant.entries()).extracting(MarketplaceCatalogEntry::id).containsExactly("ok-agent");
    }

    @Test
    void anUnreadableCatalogServesAnEmptyDirectoryInsteadOfFailingConstruction() {
        MarketplaceResourceService empty = new MarketplaceResourceService(new ObjectMapper(), "{not-json");
        assertThat(empty.entries()).isEmpty();
        assertThat(empty.search(null, null, null)).isEmpty();
    }
}
