package com.jd.genie.platform.phase2.skillruntime;

import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.contract.UserRole;
import com.jd.genie.platform.phase2.skillruntime.packageinfo.LoadedSkillPackage;
import com.jd.genie.platform.phase2.skillruntime.packageinfo.SkillManifestParser;
import com.jd.genie.platform.phase2.skillruntime.packageinfo.SkillPackageHasher;
import com.jd.genie.platform.phase2.skillruntime.packageinfo.SkillPackageLimits;
import com.jd.genie.platform.phase2.skillruntime.packageinfo.SkillPackageLoader;
import com.jd.genie.platform.phase2.skillruntime.packageinfo.SkillPackageValidator;
import com.jd.genie.platform.phase2contract.enums.SkillEntrypointRuntime;
import com.jd.genie.platform.phase2contract.error.Phase2ContractException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SkillPackageLoaderTest {
    @TempDir Path temp;
    private final CurrentUser user = new CurrentUser("tenant-a", "owner-a", "owner", "Owner", UserRole.USER);

    @Test
    void missingPackageUsesLegacySignal() {
        assertTrue(loader(temp).load(user, "skill-a").isEmpty());
    }

    @Test
    void validPackageUsesFilesystemInstructionResourcesAndEntrypoints() throws IOException {
        Path root = packageRoot(temp, user, "skill-a");
        writeValid(root, "Filesystem instruction", "resource one");
        LoadedSkillPackage loaded = loader(temp).load(user, "skill-a").orElseThrow();
        assertEquals("Filesystem instruction", loaded.instructionMarkdown());
        assertEquals(List.of("references/info.txt", "scripts/run.py"), loaded.resourceManifest());
        assertEquals(SkillEntrypointRuntime.pyodide, loaded.entrypoints().get(0).runtime());
        assertEquals(List.of("numpy>=1.26"), loaded.entrypoints().get(0).packages());
        assertEquals("resource one", new String(loader(temp).readResource(user, "skill-a", "references/info.txt").content()));
        assertFalse(loader(temp).readResource(user, "skill-a", "references/info.txt").relativePath().contains(":"));
    }

    @Test
    void invalidSkillMdFailsClosed() throws IOException {
        Path root = packageRoot(temp, user, "skill-a");
        Files.createDirectories(root);
        Files.writeString(root.resolve("SKILL.md"), "---\nschemaVersion: 1\nname: bad\n---\n\n");
        assertCode(MvpErrorCode.SKILL_PACKAGE_INVALID, () -> loader(temp).load(user, "skill-a"));
    }

    @Test
    void traversalAbsoluteSensitiveAndOwnershipReadsAreRejected() throws IOException {
        Path root = packageRoot(temp, user, "skill-a");
        writeValid(root, "instruction", "resource");
        SkillPackageLoader loader = loader(temp);
        assertCode(MvpErrorCode.SKILL_RESOURCE_NOT_FOUND, () -> loader.readResource(user, "skill-a", "../secret"));
        assertCode(MvpErrorCode.SKILL_RESOURCE_NOT_FOUND, () -> loader.readResource(user, "skill-a", "references/../info.txt"));
        assertCode(MvpErrorCode.SKILL_RESOURCE_NOT_FOUND, () -> loader.readResource(user, "skill-a", "C:\\secret"));
        assertCode(MvpErrorCode.SKILL_RESOURCE_NOT_FOUND, () -> loader.readResource(user, "skill-a", ".env"));
        CurrentUser other = new CurrentUser("tenant-a", "owner-b", "other", "Other", UserRole.USER);
        assertCode(MvpErrorCode.SKILL_RESOURCE_NOT_FOUND, () -> loader.readResource(other, "skill-a", "references/info.txt"));
    }

    @Test
    void packageHashIsStableAcrossRootAndChangesWithContent() throws IOException {
        Path first = temp.resolve("first");
        Path second = temp.resolve("second");
        writeValid(packageRoot(first, user, "skill-a"), "instruction", "same");
        writeValid(packageRoot(second, user, "skill-a"), "instruction", "same");
        String hash1 = loader(first).load(user, "skill-a").orElseThrow().packageHash();
        String hash2 = loader(second).load(user, "skill-a").orElseThrow().packageHash();
        assertEquals(hash1, hash2);
        assertTrue(hash1.matches("[0-9a-f]{64}"));
        Files.writeString(packageRoot(second, user, "skill-a").resolve("references/info.txt"), "changed");
        assertNotEquals(hash1, loader(second).load(user, "skill-a").orElseThrow().packageHash());
    }

    @Test
    void resourceSizeLimitFailsClosed() throws IOException {
        Path root = packageRoot(temp, user, "skill-a");
        writeValid(root, "instruction", "resource");
        Files.write(root.resolve("assets/too-large.bin"), new byte[SkillPackageLimits.MAX_RESOURCE_BYTES + 1]);
        assertCode(MvpErrorCode.SKILL_PACKAGE_INVALID, () -> loader(temp).load(user, "skill-a"));
    }

    @Test
    void symlinkEscapeIsRejectedWhenPlatformAllowsSymlinks() throws IOException {
        Path root = packageRoot(temp, user, "skill-a");
        writeValid(root, "instruction", "resource");
        Path outside = temp.resolve("outside.txt");
        Files.writeString(outside, "outside");
        Path link = root.resolve("references/link.txt");
        try { Files.createSymbolicLink(link, outside); }
        catch (UnsupportedOperationException | IOException | SecurityException e) {
            org.junit.jupiter.api.Assumptions.abort("symlink creation unavailable: " + e.getClass().getSimpleName());
        }
        assertCode(MvpErrorCode.SKILL_PACKAGE_INVALID, () -> loader(temp).load(user, "skill-a"));
    }

    @Test
    void ancestorSymlinkEscapeIsRejectedWhenPlatformAllowsSymlinks() throws IOException {
        Path outside = temp.resolveSibling(temp.getFileName() + "-outside");
        writeValid(outside.resolve("owner-a/skill-a"), "instruction", "resource");
        Files.createDirectories(temp.resolve("users"));
        try { Files.createSymbolicLink(temp.resolve("users/tenant-a"), outside); }
        catch (UnsupportedOperationException | IOException | SecurityException e) {
            org.junit.jupiter.api.Assumptions.abort("symlink creation unavailable: " + e.getClass().getSimpleName());
        }
        assertCode(MvpErrorCode.SKILL_PACKAGE_INVALID, () -> loader(temp).load(user, "skill-a"));
    }

    private SkillPackageLoader loader(Path root) {
        SkillPackageValidator validator = new SkillPackageValidator();
        return new SkillPackageLoader(root.toString(), new SkillManifestParser(), validator, new SkillPackageHasher());
    }
    private Path packageRoot(Path root, CurrentUser current, String skillId) {
        return root.resolve("users").resolve(current.tenantId()).resolve(current.userId()).resolve(skillId);
    }
    private void writeValid(Path root, String instruction, String resource) throws IOException {
        Files.createDirectories(root.resolve("scripts"));
        Files.createDirectories(root.resolve("references"));
        Files.createDirectories(root.resolve("assets"));
        Files.writeString(root.resolve("SKILL.md"), """
            ---
            schemaVersion: 1
            name: example
            description: example package
            version: 1.0.0
            entrypoints:
              - name: run
                runtime: pyodide
                script: scripts/run.py
                packages:
                  - numpy>=1.26
            ---

            %s
            """.formatted(instruction));
        Files.writeString(root.resolve("scripts/run.py"), "def run(input): return input");
        Files.writeString(root.resolve("references/info.txt"), resource);
    }
    private void assertCode(MvpErrorCode expected, org.junit.jupiter.api.function.Executable executable) {
        Phase2ContractException error = assertThrows(Phase2ContractException.class, executable);
        assertEquals(expected, error.errorCode());
    }
}
