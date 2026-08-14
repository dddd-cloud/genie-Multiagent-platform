package com.jd.genie.platform.phase2.skillruntime.packageinfo;

import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2contract.error.Phase2ContractException;
import org.springframework.stereotype.Component;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

@Component
public class SkillPackageValidator {
    private static final Set<String> ALLOWED_TOP_LEVEL = Set.of("SKILL.md", "scripts", "references", "templates", "assets");
    private static final Set<String> SENSITIVE_NAMES = Set.of(".env", ".env.local", "credentials", "credentials.json",
        "credential.json", "secrets", "secrets.json", "secret.json", "id_rsa", "id_ed25519");

    public String normalizeRelativePath(String relativePath, MvpErrorCode code) {
        if (relativePath == null || relativePath.isBlank()) throw invalid(code, "relative path required");
        String portable = relativePath.replace('\\', '/');
        if (portable.startsWith("/") || portable.matches("^[A-Za-z]:.*")) throw invalid(code, "absolute path rejected");
        for (String segment : portable.split("/", -1)) {
            if (segment.equals("..")) throw invalid(code, "path traversal rejected");
        }
        Path normalized;
        try { normalized = Path.of(portable).normalize(); }
        catch (RuntimeException e) { throw invalid(code, "invalid path"); }
        String result = normalized.toString().replace('\\', '/');
        if (result.isBlank() || result.equals(".") || result.equals("..") || result.startsWith("../"))
            throw invalid(code, "path traversal rejected");
        for (Path part : normalized) {
            String name = part.toString().toLowerCase(Locale.ROOT);
            if (name.equals("..") || isSensitiveName(name)) throw invalid(code, "sensitive path rejected");
        }
        return result;
    }

    public boolean keepPackageFile(String relativePath) {
        String normalized = normalizeRelativePath(relativePath, MvpErrorCode.SKILL_PACKAGE_INVALID);
        String top = normalized.contains("/") ? normalized.substring(0, normalized.indexOf('/')) : normalized;
        return ALLOWED_TOP_LEVEL.contains(top);
    }

    public void validatePackagePath(String relativePath) {
        if (!keepPackageFile(relativePath)) {
            throw invalid(MvpErrorCode.SKILL_PACKAGE_INVALID, "unsupported package path");
        }
    }

    private boolean isSensitiveName(String name) {
        return SENSITIVE_NAMES.contains(name) || name.startsWith(".env.") || name.endsWith(".pem")
            || name.endsWith(".key") || name.endsWith(".p12") || name.endsWith(".pfx");
    }

    private Phase2ContractException invalid(MvpErrorCode code, String message) {
        return new Phase2ContractException(code, message);
    }
}
