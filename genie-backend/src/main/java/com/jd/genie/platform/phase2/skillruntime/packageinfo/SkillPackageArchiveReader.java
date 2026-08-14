package com.jd.genie.platform.phase2.skillruntime.packageinfo;

import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2contract.dto.SkillEntrypointView;
import com.jd.genie.platform.phase2contract.error.Phase2ContractException;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
public class SkillPackageArchiveReader {
    private static final String SKILL_MD = "SKILL.md";

    private final SkillPackageValidator validator;
    private final SkillManifestParser parser;

    public SkillPackageArchiveReader(SkillPackageValidator validator, SkillManifestParser parser) {
        this.validator = validator;
        this.parser = parser;
    }

    public ExtractedPackage read(byte[] zipBytes) {
        if (zipBytes == null || zipBytes.length == 0) {
            throw invalid("zip required");
        }
        if (zipBytes.length > SkillPackageLimits.MAX_IMPORT_ZIP_BYTES) {
            throw invalid("zip too large");
        }
        Map<String, byte[]> raw = readEntries(zipBytes);
        if (raw.isEmpty()) {
            throw invalid("SKILL.md missing");
        }
        String prefix = detectPrefix(raw.keySet());
        Map<String, byte[]> files = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> entry : raw.entrySet()) {
            String relative = stripPrefix(entry.getKey(), prefix);
            if (relative.isEmpty()) {
                continue;
            }
            if (!validator.keepPackageFile(relative)) {
                continue;
            }
            if (files.put(relative, entry.getValue()) != null) {
                throw invalid("duplicate package path");
            }
        }
        byte[] skillMd = files.get(SKILL_MD);
        if (skillMd == null) {
            throw invalid("SKILL.md missing");
        }
        if (skillMd.length > SkillPackageLimits.MAX_SKILL_MD_BYTES) {
            throw invalid("package file too large");
        }
        SkillManifest manifest = parser.parse(skillMd);
        validateEntrypoints(manifest.entrypoints(), files.keySet());
        return new ExtractedPackage(manifest, Map.copyOf(files));
    }

    private Map<String, byte[]> readEntries(byte[] zipBytes) {
        Map<String, byte[]> raw = new LinkedHashMap<>();
        long total = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = normalizeEntryName(entry.getName());
                if (name.isEmpty() || entry.isDirectory() || name.endsWith("/")) {
                    continue;
                }
                rejectUnsafeName(name);
                long claimed = entry.getSize();
                if (claimed > SkillPackageLimits.MAX_PACKAGE_BYTES) {
                    throw invalid("uncompressed package too large");
                }
                boolean skillMd = isSkillMd(name);
                int fileLimit = skillMd ? SkillPackageLimits.MAX_SKILL_MD_BYTES : SkillPackageLimits.MAX_RESOURCE_BYTES;
                byte[] content = readLimited(zip, fileLimit, total);
                total = Math.addExact(total, content.length);
                if (total > SkillPackageLimits.MAX_PACKAGE_BYTES) {
                    throw invalid("uncompressed package too large");
                }
                if (raw.size() >= SkillPackageLimits.MAX_FILE_COUNT) {
                    throw invalid("too many package files");
                }
                if (raw.put(name, content) != null) {
                    throw invalid("duplicate zip entry");
                }
            }
        } catch (Phase2ContractException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw invalid("zip cannot be read", e);
        }
        return raw;
    }

    private byte[] readLimited(ZipInputStream zip, int fileLimit, long already) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        long read = 0;
        while ((n = zip.read(buf)) >= 0) {
            if (n == 0) {
                continue;
            }
            read += n;
            if (read > fileLimit) {
                throw invalid("package file too large");
            }
            if (already + read > SkillPackageLimits.MAX_PACKAGE_BYTES) {
                throw invalid("uncompressed package too large");
            }
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    private String detectPrefix(Set<String> names) {
        if (names.contains(SKILL_MD)) {
            return "";
        }
        Set<String> tops = new HashSet<>();
        for (String name : names) {
            int slash = name.indexOf('/');
            tops.add(slash < 0 ? name : name.substring(0, slash));
        }
        if (tops.size() != 1) {
            throw invalid("SKILL.md missing");
        }
        String top = tops.iterator().next();
        String nested = top + "/" + SKILL_MD;
        if (!names.contains(nested)) {
            throw invalid("SKILL.md missing");
        }
        return top + "/";
    }

    private String stripPrefix(String name, String prefix) {
        if (prefix.isEmpty()) {
            return name;
        }
        if (!name.startsWith(prefix)) {
            throw invalid("SKILL.md missing");
        }
        return name.substring(prefix.length());
    }

    private void validateEntrypoints(java.util.List<SkillEntrypointView> entries, Set<String> paths) {
        HashSet<String> names = new HashSet<>();
        for (SkillEntrypointView entry : entries) {
            if (!names.add(entry.name())) {
                throw invalid("duplicate entrypoint name");
            }
            String script = validator.normalizeRelativePath(entry.script(), MvpErrorCode.SKILL_PACKAGE_INVALID);
            if (!script.startsWith("scripts/") || !paths.contains(script)) {
                throw invalid("entrypoint script missing");
            }
        }
    }

    private String normalizeEntryName(String raw) {
        if (raw == null || raw.isBlank()) {
            throw invalid("path traversal rejected");
        }
        if (raw.indexOf('\0') >= 0) {
            throw invalid("path traversal rejected");
        }
        String portable = raw.replace('\\', '/').replaceAll("^(\\./)+", "");
        while (portable.startsWith("./")) {
            portable = portable.substring(2);
        }
        return portable;
    }

    private void rejectUnsafeName(String name) {
        if (name.startsWith("/") || name.matches("^[A-Za-z]:.*")) {
            throw invalid("absolute path rejected");
        }
        for (String segment : name.split("/", -1)) {
            if (segment.equals("..")) {
                throw invalid("path traversal rejected");
            }
        }
    }

    private boolean isSkillMd(String name) {
        return name.equals(SKILL_MD) || name.endsWith("/" + SKILL_MD);
    }

    private Phase2ContractException invalid(String message) {
        return new Phase2ContractException(MvpErrorCode.SKILL_PACKAGE_INVALID, message);
    }

    private Phase2ContractException invalid(String message, Throwable cause) {
        return new Phase2ContractException(MvpErrorCode.SKILL_PACKAGE_INVALID, message, cause);
    }

    public record ExtractedPackage(SkillManifest manifest, Map<String, byte[]> files) {
        public ExtractedPackage {
            files = files == null ? Map.of() : Map.copyOf(files);
        }
    }
}
