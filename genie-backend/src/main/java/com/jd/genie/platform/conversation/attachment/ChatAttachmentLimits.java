package com.jd.genie.platform.conversation.attachment;

import java.util.Set;

public final class ChatAttachmentLimits {
    public static final int MAX_FILES = 10;
    public static final long MAX_FILE_BYTES = 10L * 1024 * 1024;
    public static final int MAX_EXTRACT_CODE_POINTS = 80_000;
    public static final int MAX_PROMPT_CODE_POINTS = 400_000;
    public static final int MAX_FILE_NAME_LENGTH = 255;

    public static final Set<String> ALLOWED_TYPES = Set.of(
        "md", "txt", "py", "csv", "json", "doc", "docx", "pdf"
    );

    private ChatAttachmentLimits() {
    }
}
