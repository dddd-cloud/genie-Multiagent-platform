package com.jd.genie.platform.phase2.configuration.memory.validation;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class MemoryMarkdownGuard {
    private static final Pattern DANGEROUS_LINK = Pattern.compile("(?i)\\]\\((?:javascript|data|vbscript):");
    private static final Pattern HTML_EVENT = Pattern.compile("(?i)<[^>]+\\son\\w+\\s*=");
    private static final Pattern PATH_TRAVERSAL = Pattern.compile("(^|[\\\\/])\\.\\.([\\\\/]|$)");

    public boolean isUnsafe(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String trimmed = value.trim();
        return trimmed.startsWith("---")
            || trimmed.contains("```")
            || trimmed.contains("<script")
            || HTML_EVENT.matcher(value).find()
            || DANGEROUS_LINK.matcher(value).find()
            || PATH_TRAVERSAL.matcher(value).find()
            || Pattern.compile("(?m)^#{1,2}\\s+").matcher(value).find()
            || Pattern.compile("(?im)^\\s*(schemaVersion|updatedAt)\\s*[:=]").matcher(value).find();
    }
}
