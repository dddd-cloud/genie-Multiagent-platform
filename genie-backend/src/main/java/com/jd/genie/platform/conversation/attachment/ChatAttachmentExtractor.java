package com.jd.genie.platform.conversation.attachment;

import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.conversation.exception.ConversationException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class ChatAttachmentExtractor {
    public record ExtractedText(String text, boolean truncated) {
    }

    private ChatAttachmentExtractor() {
    }

    public static String fileTypeOf(String fileName) {
        String name = fileName == null ? "" : fileName.trim();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            throw new ConversationException(MvpErrorCode.VALIDATION_ERROR, "unsupported file type");
        }
        String type = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!ChatAttachmentLimits.ALLOWED_TYPES.contains(type)) {
            throw new ConversationException(MvpErrorCode.VALIDATION_ERROR, "unsupported file type");
        }
        return type;
    }

    public static ExtractedText extract(String fileType, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new ConversationException(MvpErrorCode.VALIDATION_ERROR, "file is empty");
        }
        String raw = switch (fileType) {
            case "md", "txt", "py", "csv", "json" -> decodeText(bytes);
            case "docx" -> extractDocx(bytes);
            case "doc" -> extractDoc(bytes);
            case "pdf" -> extractPdf(bytes);
            default -> throw new ConversationException(MvpErrorCode.VALIDATION_ERROR, "unsupported file type");
        };
        return truncate(raw == null ? "" : raw);
    }

    static ExtractedText truncate(String raw) {
        String normalized = raw.replace("\u0000", "").trim();
        int limit = ChatAttachmentLimits.MAX_EXTRACT_CODE_POINTS;
        int codePoints = normalized.codePointCount(0, normalized.length());
        if (codePoints <= limit) {
            return new ExtractedText(normalized, false);
        }
        int end = normalized.offsetByCodePoints(0, limit);
        return new ExtractedText(normalized.substring(0, end), true);
    }

    private static String decodeText(byte[] bytes) {
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
            return text.substring(1);
        }
        return text;
    }

    private static String extractDocx(byte[] bytes) {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes));
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        } catch (Exception exception) {
            throw new ConversationException(MvpErrorCode.VALIDATION_ERROR, "failed to read Word file", exception);
        }
    }

    private static String extractDoc(byte[] bytes) {
        try (HWPFDocument document = new HWPFDocument(new ByteArrayInputStream(bytes));
             WordExtractor extractor = new WordExtractor(document)) {
            return extractor.getText();
        } catch (Exception exception) {
            throw new ConversationException(MvpErrorCode.VALIDATION_ERROR, "failed to read Word file", exception);
        }
    }

    private static String extractPdf(byte[] bytes) {
        try (PDDocument document = PDDocument.load(bytes)) {
            if (document.isEncrypted()) {
                throw new ConversationException(MvpErrorCode.VALIDATION_ERROR, "encrypted PDF is not supported");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        } catch (ConversationException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new ConversationException(MvpErrorCode.VALIDATION_ERROR, "failed to read PDF file", exception);
        }
    }
}
