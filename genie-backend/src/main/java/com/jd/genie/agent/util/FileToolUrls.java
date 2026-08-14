package com.jd.genie.agent.util;

public final class FileToolUrls {
    private static final String DEFAULT_BASE = "http://127.0.0.1:1601";

    private FileToolUrls() {
    }

    public static boolean isHttpUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        String trimmed = url.trim();
        return trimmed.startsWith("http://") || trimmed.startsWith("https://");
    }

    public static String publicUrl(String raw, String interpreterBase, String kind, String requestId, String fileName) {
        if (isHttpUrl(raw)) {
            return raw.trim();
        }
        String base = interpreterBase == null || interpreterBase.isBlank() ? DEFAULT_BASE : interpreterBase.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (raw != null) {
            String stripped = raw.trim().replaceFirst("(?i)^None/?", "");
            if (stripped.startsWith("download/") || stripped.startsWith("preview/")) {
                return base + "/v1/file_tool/" + stripped;
            }
        }
        return base + "/v1/file_tool/" + kind + "/" + requestId + "/" + fileName;
    }
}
