package com.jd.genie.platform.phase2.skillruntime.packageinfo;

import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2contract.dto.SkillEntrypointView;
import com.jd.genie.platform.phase2contract.enums.SkillEntrypointRuntime;
import com.jd.genie.platform.phase2contract.error.Phase2ContractException;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class SkillManifestParser {
    private static final Pattern PACKAGE_SPEC = Pattern.compile("^[A-Za-z0-9](?:[A-Za-z0-9._-]*[A-Za-z0-9])?(?:[<>=!~]{1,2}[A-Za-z0-9](?:[A-Za-z0-9.*+_-]*[A-Za-z0-9*])?)?$");
    private static final int MAX_ENTRYPOINTS = 32;
    private static final int MAX_PACKAGES = 32;

    public SkillManifest parse(byte[] bytes) {
        String source = new String(bytes, StandardCharsets.UTF_8).replace("\r\n", "\n");
        if (!source.startsWith("---\n")) throw invalid("SKILL.md frontmatter required");
        int close = source.indexOf("\n---\n", 4);
        if (close < 0) throw invalid("SKILL.md frontmatter not closed");
        String body = source.substring(close + 5).trim();
        if (body.isEmpty()) throw invalid("SKILL.md instruction required");

        Map<String, String> root = new LinkedHashMap<>();
        List<MutableEntrypoint> entries = new ArrayList<>();
        MutableEntrypoint current = null;
        boolean inEntrypoints = false;
        boolean inPackages = false;
        for (String raw : source.substring(4, close).split("\n", -1)) {
            if (raw.isBlank() || raw.stripLeading().startsWith("#")) continue;
            int indent = raw.length() - raw.stripLeading().length();
            String line = raw.strip();
            if (indent == 0) {
                current = null;
                inPackages = false;
                if (line.equals("entrypoints:")) { inEntrypoints = true; continue; }
                inEntrypoints = false;
                putScalar(root, line);
            } else if (inEntrypoints && indent == 2 && line.startsWith("- ")) {
                if (entries.size() >= MAX_ENTRYPOINTS) throw invalid("too many entrypoints");
                current = new MutableEntrypoint();
                entries.add(current);
                inPackages = false;
                putScalar(current.values, line.substring(2));
            } else if (current != null && indent == 4 && line.equals("packages:")) {
                inPackages = true;
            } else if (current != null && indent == 6 && inPackages && line.startsWith("- ")) {
                if (current.packages.size() >= MAX_PACKAGES) throw invalid("too many packages");
                String spec = scalar(line.substring(2));
                validatePackageSpec(spec);
                current.packages.add(spec);
            } else if (current != null && indent == 4) {
                inPackages = false;
                putScalar(current.values, line);
            } else {
                throw invalid("invalid frontmatter indentation");
            }
        }
        if (!"1".equals(required(root, "schemaVersion"))) throw invalid("unsupported schemaVersion");
        return new SkillManifest(required(root, "name"), required(root, "description"), required(root, "version"),
            body, entries.stream().map(this::toView).toList());
    }

    private SkillEntrypointView toView(MutableEntrypoint entry) {
        SkillEntrypointRuntime runtime;
        try { runtime = SkillEntrypointRuntime.valueOf(required(entry.values, "runtime")); }
        catch (IllegalArgumentException e) { throw invalid("unsupported entrypoint runtime"); }
        if (runtime != SkillEntrypointRuntime.pyodide && !entry.packages.isEmpty())
            throw invalid("packages are only valid for pyodide");
        return new SkillEntrypointView(required(entry.values, "name"), runtime, required(entry.values, "script"),
            entry.values.get("description"), entry.values.get("inputSchemaJson"), entry.packages);
    }

    private void validatePackageSpec(String spec) {
        String lower = spec.toLowerCase(java.util.Locale.ROOT);
        if (spec.length() > 128 || lower.endsWith(".whl") || lower.contains("http") || lower.contains("file:")
            || lower.contains("git+") || spec.contains("/") || spec.contains("\\") || spec.contains("@")
            || spec.contains(":") || spec.contains("..") || !PACKAGE_SPEC.matcher(spec).matches())
            throw invalid("invalid pyodide package spec");
    }
    private void putScalar(Map<String, String> values, String line) {
        int colon = line.indexOf(':');
        if (colon <= 0) throw invalid("invalid frontmatter scalar");
        String key = line.substring(0, colon).trim();
        if (key.isBlank() || values.putIfAbsent(key, scalar(line.substring(colon + 1))) != null)
            throw invalid("duplicate frontmatter field");
    }
    private String scalar(String value) {
        String result = value.trim();
        if (result.length() >= 2 && ((result.startsWith("\"") && result.endsWith("\""))
            || (result.startsWith("'") && result.endsWith("'")))) result = result.substring(1, result.length() - 1);
        return result.trim();
    }
    private String required(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || value.isBlank()) throw invalid("missing " + key);
        return value;
    }
    private Phase2ContractException invalid(String message) {
        return new Phase2ContractException(MvpErrorCode.SKILL_PACKAGE_INVALID, message);
    }
    private static final class MutableEntrypoint {
        private final Map<String, String> values = new LinkedHashMap<>();
        private final List<String> packages = new ArrayList<>();
    }
}
