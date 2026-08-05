package com.jd.genie.platform.phase2.configuration.memory.validation;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

@Component
public class MemorySecretFilter {
    private static final List<Pattern> SECRET_PATTERNS = List.of(
        Pattern.compile("(?i)\\b(passwd|password|token|api[_-]?key|apikey|secret)\\s*[:=]\\s*\\S{6,}"),
        Pattern.compile("(?i)Authorization\\s*:\\s*Bearer\\s+\\S+"),
        Pattern.compile("\\beyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\b"),
        Pattern.compile("-----BEGIN\\s+(?:RSA\\s+|EC\\s+|OPENSSH\\s+)?PRIVATE KEY-----"),
        Pattern.compile("\\bAKIA[0-9A-Z]{16}\\b"),
        Pattern.compile("(?i)\\b(cookie|session)\\s*[:=]\\s*\\S{8,}"),
        Pattern.compile("(?i)\\b(?:id[_-]?card|bank[_-]?card)\\s*[:=]\\s*\\S{6,}"),
        Pattern.compile("\\b(?:[A-Fa-f0-9]{32}|[A-Fa-f0-9]{64})\\b")
    );

    public boolean containsSecret(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        for (Pattern pattern : SECRET_PATTERNS) {
            if (pattern.matcher(value).find()) {
                return true;
            }
        }
        return false;
    }
}
