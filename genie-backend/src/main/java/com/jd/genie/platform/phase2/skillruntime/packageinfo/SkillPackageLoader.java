package com.jd.genie.platform.phase2.skillruntime.packageinfo;

import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2contract.dto.SkillEntrypointView;
import com.jd.genie.platform.phase2contract.dto.SkillResource;
import com.jd.genie.platform.phase2contract.error.Phase2ContractException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

@Component
public class SkillPackageLoader {
    private final Path skillRoot;
    private final SkillManifestParser parser;
    private final SkillPackageValidator validator;
    private final SkillPackageHasher hasher;

    public SkillPackageLoader(@Value("${genie.skill.root:${GENIE_SKILL_ROOT:./skills}}") String skillRoot,
                              SkillManifestParser parser, SkillPackageValidator validator, SkillPackageHasher hasher) {
        this.skillRoot = Path.of(skillRoot).toAbsolutePath().normalize();
        this.parser = parser;
        this.validator = validator;
        this.hasher = hasher;
    }

    public Optional<LoadedSkillPackage> load(CurrentUser user, String skillId) {
        Path root = userPackageRoot(user, skillId);
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return Optional.empty();
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root))
            throw invalid("skill package root must be a real directory");
        try {
            Path realSkillRoot = skillRoot.toRealPath();
            Path realRoot = root.toRealPath(LinkOption.NOFOLLOW_LINKS);
            if (!realRoot.startsWith(realSkillRoot)) throw invalid("skill package root escaped configured root");
            List<SkillPackageHasher.PackageFile> files = scan(realRoot);
            SkillPackageHasher.PackageFile skillMd = files.stream().filter(f -> f.relativePath().equals("SKILL.md"))
                .findFirst().orElseThrow(() -> invalid("SKILL.md missing"));
            SkillManifest manifest = parser.parse(skillMd.content());
            validateEntrypoints(manifest.entrypoints(), files);
            List<String> resources = files.stream().map(SkillPackageHasher.PackageFile::relativePath)
                .filter(path -> !path.equals("SKILL.md")).toList();
            return Optional.of(new LoadedSkillPackage(manifest.version(), manifest.name(), manifest.description(),
                manifest.instructionMarkdown(), hasher.filesystemHash(files), resources, manifest.entrypoints()));
        } catch (Phase2ContractException e) { throw e; }
        catch (IOException | RuntimeException e) { throw invalid("skill package cannot be read", e); }
    }

    public SkillResource readResource(CurrentUser user, String skillId, String relativePath) {
        String normalized = validator.normalizeRelativePath(relativePath, MvpErrorCode.SKILL_RESOURCE_NOT_FOUND);
        if (normalized.equals("SKILL.md"))
            throw new Phase2ContractException(MvpErrorCode.SKILL_RESOURCE_NOT_FOUND, "SKILL.md is not a resource");
        LoadedSkillPackage snapshot = load(user, skillId)
            .orElseThrow(() -> new Phase2ContractException(MvpErrorCode.SKILL_RESOURCE_NOT_FOUND, "skill package missing"));
        if (!snapshot.resourceManifest().contains(normalized))
            throw new Phase2ContractException(MvpErrorCode.SKILL_RESOURCE_NOT_FOUND, "resource not found");
        Path root = userPackageRoot(user, skillId);
        try {
            Path realRoot = root.toRealPath(LinkOption.NOFOLLOW_LINKS);
            Path resource = realRoot.resolve(normalized).normalize();
            Path realResource = resource.toRealPath();
            if (!realResource.startsWith(realRoot) || !Files.isRegularFile(realResource, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(resource))
                throw new Phase2ContractException(MvpErrorCode.SKILL_RESOURCE_NOT_FOUND, "resource escaped package root");
            if (Files.size(realResource) > SkillPackageLimits.MAX_RESOURCE_BYTES)
                throw new Phase2ContractException(MvpErrorCode.SKILL_RESOURCE_NOT_FOUND, "resource too large");
            return new SkillResource(skillId, normalized, contentType(normalized), Files.readAllBytes(realResource));
        } catch (Phase2ContractException e) { throw e; }
        catch (IOException e) { throw new Phase2ContractException(MvpErrorCode.SKILL_RESOURCE_NOT_FOUND, "resource cannot be read"); }
    }

    private List<SkillPackageHasher.PackageFile> scan(Path realRoot) throws IOException {
        List<SkillPackageHasher.PackageFile> files = new ArrayList<>();
        long total = 0;
        try (Stream<Path> stream = Files.walk(realRoot)) {
            for (Path path : stream.toList()) {
                if (path.equals(realRoot)) continue;
                if (Files.isSymbolicLink(path)) throw invalid("symbolic links are not allowed in skill packages");
                if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) throw invalid("device or special file rejected");
                    continue;
                }
                Path real = path.toRealPath();
                if (!real.startsWith(realRoot)) throw invalid("package path escaped root");
                String relative = realRoot.relativize(path).toString().replace('\\', '/');
                validator.validatePackagePath(relative);
                long size = Files.size(path);
                long limit = relative.equals("SKILL.md") ? SkillPackageLimits.MAX_SKILL_MD_BYTES : SkillPackageLimits.MAX_RESOURCE_BYTES;
                if (size > limit) throw invalid("package file too large");
                total = Math.addExact(total, size);
                if (total > SkillPackageLimits.MAX_PACKAGE_BYTES) throw invalid("skill package too large");
                if (files.size() >= SkillPackageLimits.MAX_FILE_COUNT) throw invalid("too many package files");
                files.add(new SkillPackageHasher.PackageFile(relative, Files.readAllBytes(path)));
            }
        }
        files.sort(Comparator.comparing(SkillPackageHasher.PackageFile::relativePath));
        return List.copyOf(files);
    }

    private void validateEntrypoints(List<SkillEntrypointView> entries, List<SkillPackageHasher.PackageFile> files) {
        List<String> paths = files.stream().map(SkillPackageHasher.PackageFile::relativePath).toList();
        java.util.HashSet<String> names = new java.util.HashSet<>();
        for (SkillEntrypointView entry : entries) {
            if (!names.add(entry.name())) throw invalid("duplicate entrypoint name");
            String script = validator.normalizeRelativePath(entry.script(), MvpErrorCode.SKILL_PACKAGE_INVALID);
            if (!script.startsWith("scripts/") || !paths.contains(script)) throw invalid("entrypoint script missing");
        }
    }

    private Path userPackageRoot(CurrentUser user, String skillId) {
        requireIdentity(user == null ? null : user.tenantId(), "tenantId");
        requireIdentity(user == null ? null : user.userId(), "ownerId");
        requireIdentity(skillId, "skillId");
        return skillRoot.resolve("users").resolve(user.tenantId()).resolve(user.userId()).resolve(skillId).normalize();
    }
    private void requireIdentity(String value, String name) {
        if (value == null || value.isBlank() || !value.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}"))
            throw new Phase2ContractException(MvpErrorCode.VALIDATION_ERROR, name + " invalid");
    }
    private String contentType(String path) {
        String value = path.toLowerCase(Locale.ROOT);
        if (value.endsWith(".md")) return "text/markdown";
        if (value.endsWith(".json")) return "application/json";
        if (value.endsWith(".txt") || value.endsWith(".py") || value.endsWith(".js")) return "text/plain";
        return "application/octet-stream";
    }
    private Phase2ContractException invalid(String message) {
        return new Phase2ContractException(MvpErrorCode.SKILL_PACKAGE_INVALID, message);
    }
    private Phase2ContractException invalid(String message, Throwable cause) {
        return new Phase2ContractException(MvpErrorCode.SKILL_PACKAGE_INVALID, message, cause);
    }
}
