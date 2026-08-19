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
        assertThat(service.entries()).hasSize(8);
        assertThat(service.entries()).extracting(MarketplaceCatalogEntry::type)
            .containsExactlyInAnyOrder(MarketplaceResourceType.AGENT, MarketplaceResourceType.AGENT,
                MarketplaceResourceType.TEAM, MarketplaceResourceType.TEAM,
                MarketplaceResourceType.SKILL, MarketplaceResourceType.SKILL,
                MarketplaceResourceType.MCP, MarketplaceResourceType.MCP);
        assertThat(service.entries()).allSatisfy(entry -> {
            assertThat(entry.draft().toString()).doesNotContainIgnoringCase("credential_envelope");
            assertThat(entry.draft().toString()).doesNotContain("apiKey");
            assertThat(entry.draft().toString()).doesNotContain("tenantId");
        });
    }

    @Test
    void searchFiltersByTypeAndQuery() {
        assertThat(service.search(MarketplaceResourceType.SKILL, null, "pyodide"))
            .extracting(MarketplaceResourceView::id)
            .containsExactlyInAnyOrder("skill-browser-python-report", "skill-jupyterlite-notebook");
        assertThat(service.search(null, null, "github"))
            .extracting(MarketplaceResourceView::id)
            .containsExactly("mcp-github-public-tools");
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
