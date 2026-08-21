package com.jd.genie.agent.util;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

public final class FileToolUrls {
    private static final String DEFAULT_BASE = "http://127.0.0.1:1601";
    private static final String FILE_TOOL_PREFIX = "/v1/file_tool/";

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
            if (stripped.startsWith("http://") || stripped.startsWith("https://")) {
                return stripped;
            }
            if (stripped.startsWith(FILE_TOOL_PREFIX) || stripped.startsWith("v1/file_tool/")) {
                return stripped.startsWith("/") ? base + stripped : base + "/" + stripped;
            }
            if (stripped.startsWith("download/") || stripped.startsWith("preview/")) {
                return base + "/v1/file_tool/" + stripped;
            }
        }
        return base + FILE_TOOL_PREFIX + kind + "/" + requestId + "/" + fileName;
    }

    /**
     * File-tool URLs store the upload namespace in {@code /v1/file_tool/{download|preview}/{scope}/...}.
     */
    public static String scopeFromUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String path = url.trim();
        int idx = path.indexOf(FILE_TOOL_PREFIX);
        if (idx < 0) {
            return null;
        }
        String rest = path.substring(idx + FILE_TOOL_PREFIX.length());
        String[] parts = rest.split("/", 3);
        if (parts.length < 2) {
            return null;
        }
        if (!"download".equals(parts[0]) && !"preview".equals(parts[0])) {
            return null;
        }
        String scope = parts[1];
        if (scope.isBlank()) {
            return null;
        }
        return URLDecoder.decode(scope, StandardCharsets.UTF_8);
    }
}
