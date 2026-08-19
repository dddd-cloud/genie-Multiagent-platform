package com.jd.genie.platform.workspace;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Server-computed remote namespace. The browser never supplies the user dimension.
 */
public final class WorkspaceRequestIds {
    private WorkspaceRequestIds() {
    }

    public static String forConversation(String tenantId, String userId, String conversationId) {
        String payload = tenantId + ":" + userId + ":" + conversationId;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(payload.getBytes(StandardCharsets.UTF_8));
            return "workspace-v1-" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }
}
