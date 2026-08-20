package com.jd.genie.platform.conversation.attachment;

import com.jd.genie.platform.conversation.entity.ConversationAttachmentEntity;

import java.util.List;

public final class ChatAttachmentPrompt {
    static final String FILES_START = "<user_uploaded_files>";
    static final String FILES_END = "</user_uploaded_files>";

    public record Prompts(String routingQuery, String specialistQuery) {
    }

    private ChatAttachmentPrompt() {
    }

    public static Prompts prompts(String query, List<ConversationAttachmentEntity> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            String text = query == null ? "" : query;
            return new Prompts(text, text);
        }
        return new Prompts(catalog(query, attachments), enrich(query, attachments));
    }

    /**
     * Router and planner must not see extracted file bodies: resumes often contain
     * words like 团队/生成/agent and would be misclassified as resource creation.
     */
    public static String catalog(String query, List<ConversationAttachmentEntity> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return query == null ? "" : query;
        }
        StringBuilder block = new StringBuilder();
        block.append("\n\n用户上传了以下文件，请阅读这些文件后来回答：\n");
        for (ConversationAttachmentEntity attachment : attachments) {
            String name = attachment.getFileName() == null ? "file" : attachment.getFileName();
            String type = attachment.getFileType() == null ? "" : attachment.getFileType();
            block.append("- ")
                    .append(escape(name))
                    .append(" (")
                    .append(escape(type))
                    .append(")\n");
        }
        return (query == null ? "" : query) + block;
    }

    public static String withoutUploadedFileBodies(String query) {
        if (query == null || query.isEmpty()) {
            return query == null ? "" : query;
        }
        int start = query.indexOf(FILES_START);
        if (start < 0) {
            return query;
        }
        int from = start;
        while (from > 0 && query.charAt(from - 1) == '\n') {
            from--;
        }
        int end = query.indexOf(FILES_END, start);
        if (end < 0) {
            return query.substring(0, from).trim();
        }
        String after = query.substring(end + FILES_END.length());
        return (query.substring(0, from) + after).trim();
    }

    public static String enrich(String query, List<ConversationAttachmentEntity> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return query == null ? "" : query;
        }
        StringBuilder block = new StringBuilder();
        block.append("\n\n<user_uploaded_files>\n");
        block.append("The user uploaded the following files. Read them and use them to answer.\n");
        int remaining = ChatAttachmentLimits.MAX_PROMPT_CODE_POINTS;
        for (ConversationAttachmentEntity attachment : attachments) {
            String name = attachment.getFileName() == null ? "file" : attachment.getFileName();
            String type = attachment.getFileType() == null ? "" : attachment.getFileType();
            String text = attachment.getExtractedText() == null ? "" : attachment.getExtractedText();
            boolean truncated = Boolean.TRUE.equals(attachment.getTruncated());
            if (text.codePointCount(0, text.length()) > remaining) {
                int end = text.offsetByCodePoints(0, Math.max(0, remaining));
                text = text.substring(0, end);
                truncated = true;
                remaining = 0;
            } else {
                remaining -= text.codePointCount(0, text.length());
            }
            block.append("\n<file name=\"")
                .append(escape(name))
                .append("\" type=\"")
                .append(escape(type))
                .append("\"");
            if (truncated) {
                block.append(" truncated=\"true\"");
            }
            block.append(">\n")
                .append(text)
                .append("\n</file>\n");
            if (remaining <= 0) {
                break;
            }
        }
        block.append("</user_uploaded_files>");
        return (query == null ? "" : query) + block;
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;");
    }
}
