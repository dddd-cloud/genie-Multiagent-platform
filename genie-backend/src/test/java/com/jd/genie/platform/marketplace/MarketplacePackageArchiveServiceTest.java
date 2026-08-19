package com.jd.genie.platform.marketplace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.platform.phase2.skillruntime.packageinfo.SkillManifestParser;
import com.jd.genie.platform.phase2.skillruntime.packageinfo.SkillPackageArchiveReader;
import com.jd.genie.platform.phase2.skillruntime.packageinfo.SkillPackageValidator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MarketplacePackageArchiveServiceTest {
    private final MarketplaceResourceService catalog = new MarketplaceResourceService(new ObjectMapper());
    private final MarketplacePackageArchiveService archiveService = new MarketplacePackageArchiveService();
    private final SkillPackageArchiveReader reader = new SkillPackageArchiveReader(
        new SkillPackageValidator(), new SkillManifestParser());

    @Test
    void curatedSkillEntriesProduceNormalValidatedSkillPackages() {
        for (MarketplaceCatalogEntry entry : catalog.entries()) {
            if (entry.type() != MarketplaceResourceType.SKILL) continue;
            var extracted = reader.read(archiveService.archive(entry.delivery()));
            assertThat(extracted.manifest().name()).isEqualTo(entry.draft().path("name").asText());
            assertThat(extracted.manifest().entrypoints()).isNotEmpty();
            assertThat(extracted.files()).containsKey("SKILL.md");
            assertThat(extracted.files().keySet()).anyMatch(path -> path.startsWith("scripts/"));
        }
    }
}
