package com.jd.genie.platform.phase2.skillruntime;

import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2.skillruntime.packageinfo.SkillManifestParser;
import com.jd.genie.platform.phase2.skillruntime.packageinfo.SkillPackageArchiveReader;
import com.jd.genie.platform.phase2.skillruntime.packageinfo.SkillPackageLimits;
import com.jd.genie.platform.phase2.skillruntime.packageinfo.SkillPackageValidator;
import com.jd.genie.platform.phase2contract.error.Phase2ContractException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillPackageArchiveReaderTest {
    private final SkillPackageArchiveReader reader =
        new SkillPackageArchiveReader(new SkillPackageValidator(), new SkillManifestParser());

    @Test
    void githubStyleZipWithLicenseAndAnthropicFrontmatterSucceeds() throws IOException {
        String skillMd = """
            ---
            name: brand-guidelines
            description: Apply Anthropic's official brand colors and typography.
            license: Complete terms in LICENSE.txt
            ---

            # Anthropic Brand Guidelines
            Use the official palette.
            """;
        var extracted = reader.read(zip(Map.of(
            "LICENSE.txt", "copyright",
            "README.md", "readme",
            "SKILL.md", skillMd
        )));
        assertEquals("brand-guidelines", extracted.manifest().name());
        assertEquals("1.0.0", extracted.manifest().version());
        assertTrue(extracted.files().containsKey("SKILL.md"));
        assertEquals(1, extracted.files().size());
    }

    @Test
    void zipWithSkillMdAtRootSucceeds() throws IOException {
        var extracted = reader.read(zipBytes(validRootFiles("Filesystem instruction")));
        assertEquals("imported-example", extracted.manifest().name());
        assertEquals("Filesystem instruction", extracted.manifest().instructionMarkdown());
        assertTrue(extracted.files().containsKey("SKILL.md"));
        assertTrue(extracted.files().containsKey("scripts/run.py"));
    }

    @Test
    void zipWithSingleTopLevelFolderSucceeds() throws IOException {
        Map<String, byte[]> files = new LinkedHashMap<>();
        validRootFiles("Nested instruction").forEach((path, bytes) -> files.put("skill-main/" + path, bytes));
        var extracted = reader.read(zipBytes(files));
        assertEquals("Nested instruction", extracted.manifest().instructionMarkdown());
        assertTrue(extracted.files().containsKey("SKILL.md"));
        assertTrue(extracted.files().containsKey("scripts/run.py"));
    }

    @Test
    void traversalZipIsRejected() throws IOException {
        assertCode(MvpErrorCode.SKILL_PACKAGE_INVALID, () -> reader.read(zip(Map.of(
            "../secret", "nope",
            "SKILL.md", skillMd("x")
        ))));
        assertCode(MvpErrorCode.SKILL_PACKAGE_INVALID, () -> reader.read(zip(Map.of(
            "scripts/../../etc/passwd", "nope",
            "SKILL.md", skillMd("x")
        ))));
        assertCode(MvpErrorCode.SKILL_PACKAGE_INVALID, () -> reader.read(zip(Map.of(
            "/etc/passwd", "nope",
            "SKILL.md", skillMd("x")
        ))));
        assertCode(MvpErrorCode.SKILL_PACKAGE_INVALID, () -> reader.read(zip(Map.of(
            "C:/Windows/secret", "nope",
            "SKILL.md", skillMd("x")
        ))));
    }

    @Test
    void missingSkillMdIsRejected() throws IOException {
        assertCode(MvpErrorCode.SKILL_PACKAGE_INVALID, () -> reader.read(zip(Map.of(
            "scripts/run.py", "print(1)"
        ))));
    }

    @Test
    void oversizedZipIsRejected() {
        byte[] huge = new byte[(int) SkillPackageLimits.MAX_IMPORT_ZIP_BYTES + 1];
        assertCode(MvpErrorCode.SKILL_PACKAGE_INVALID, () -> reader.read(huge));
    }

    @Test
    void oversizedPackageFileIsRejected() throws IOException {
        Map<String, byte[]> files = new LinkedHashMap<>(validRootFiles("instruction"));
        files.put("assets/too-large.bin", new byte[SkillPackageLimits.MAX_RESOURCE_BYTES + 1]);
        assertCode(MvpErrorCode.SKILL_PACKAGE_INVALID, () -> reader.read(zipBytes(files)));
    }

    private Map<String, byte[]> validRootFiles(String instruction) {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("SKILL.md", skillMd(instruction).getBytes(StandardCharsets.UTF_8));
        files.put("scripts/run.py", "def run(input):\n    return input\n".getBytes(StandardCharsets.UTF_8));
        return files;
    }

    private String skillMd(String instruction) {
        return """
            ---
            schemaVersion: 1
            name: imported-example
            description: example package
            version: 1.0.0
            entrypoints:
              - name: run
                runtime: pyodide
                script: scripts/run.py
            ---

            %s
            """.formatted(instruction);
    }

    private byte[] zip(Map<String, String> files) throws IOException {
        Map<String, byte[]> bytes = new LinkedHashMap<>();
        files.forEach((path, text) -> bytes.put(path, text.getBytes(StandardCharsets.UTF_8)));
        return zipBytes(bytes);
    }

    private byte[] zipBytes(Map<String, byte[]> files) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Map.Entry<String, byte[]> entry : files.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }

    private void assertCode(MvpErrorCode expected, org.junit.jupiter.api.function.Executable executable) {
        Phase2ContractException error = assertThrows(Phase2ContractException.class, executable);
        assertEquals(expected, error.errorCode());
    }
}
