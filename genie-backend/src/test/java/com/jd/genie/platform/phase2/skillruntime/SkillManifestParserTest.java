package com.jd.genie.platform.phase2.skillruntime;

import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2.skillruntime.packageinfo.SkillManifest;
import com.jd.genie.platform.phase2.skillruntime.packageinfo.SkillManifestParser;
import com.jd.genie.platform.phase2contract.error.Phase2ContractException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillManifestParserTest {
    private final SkillManifestParser parser = new SkillManifestParser();

    @Test
    void anthropicFrontmatterDefaultsSchemaAndVersion() {
        SkillManifest manifest = parser.parse("""
            ---
            name: brand-guidelines
            description: Apply Anthropic's official brand colors and typography.
            license: Complete terms in LICENSE.txt
            metadata:
              author: anthropic
              version: 1.0.0
            ---

            # Anthropic Brand Guidelines
            Use the official palette.
            """.getBytes(StandardCharsets.UTF_8));
        assertEquals("brand-guidelines", manifest.name());
        assertEquals("Apply Anthropic's official brand colors and typography.", manifest.description());
        assertEquals("1.0.0", manifest.version());
        assertTrue(manifest.instructionMarkdown().contains("official palette"));
        assertTrue(manifest.entrypoints().isEmpty());
    }

    @Test
    void bomPrefixedFrontmatterIsAccepted() {
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] body = """
            ---
            name: bom-skill
            description: has bom
            ---

            instructions
            """.getBytes(StandardCharsets.UTF_8);
        byte[] bytes = new byte[bom.length + body.length];
        System.arraycopy(bom, 0, bytes, 0, bom.length);
        System.arraycopy(body, 0, bytes, bom.length, body.length);
        assertEquals("bom-skill", parser.parse(bytes).name());
    }

    @Test
    void missingNameStillFails() {
        Phase2ContractException error = assertThrows(Phase2ContractException.class, () -> parser.parse("""
            ---
            description: only description
            ---

            instructions
            """.getBytes(StandardCharsets.UTF_8)));
        assertEquals(MvpErrorCode.SKILL_PACKAGE_INVALID, error.errorCode());
    }
}
