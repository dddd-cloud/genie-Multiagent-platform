package com.jd.genie.platform.conversation.attachment;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatAttachmentExtractorTest {

    @Test
    void readsUtf8TextFormatsAndStripsBom() {
        byte[] bom = new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] body = "hello csv".getBytes(StandardCharsets.UTF_8);
        byte[] bytes = new byte[bom.length + body.length];
        System.arraycopy(bom, 0, bytes, 0, bom.length);
        System.arraycopy(body, 0, bytes, bom.length, body.length);

        assertEquals("md", ChatAttachmentExtractor.fileTypeOf("notes.md"));
        assertEquals("hello csv", ChatAttachmentExtractor.extract("csv", bytes).text());
        assertEquals("{\"a\":1}", ChatAttachmentExtractor.extract("json", "{\"a\":1}".getBytes(StandardCharsets.UTF_8)).text());
        assertEquals("print(1)", ChatAttachmentExtractor.extract("py", "print(1)".getBytes(StandardCharsets.UTF_8)).text());
    }

    @Test
    void extractsPdfAndDocxText() throws Exception {
        assertTrue(ChatAttachmentExtractor.extract("pdf", pdfBytes("PDF_BODY_88")).text().contains("PDF_BODY_88"));
        assertTrue(ChatAttachmentExtractor.extract("docx", docxBytes("WORD_BODY_88")).text().contains("WORD_BODY_88"));
    }

    @Test
    void truncatesOversizedText() {
        String huge = "a".repeat(ChatAttachmentLimits.MAX_EXTRACT_CODE_POINTS + 12);
        ChatAttachmentExtractor.ExtractedText extracted = ChatAttachmentExtractor.extract(
            "txt",
            huge.getBytes(StandardCharsets.UTF_8)
        );
        assertTrue(extracted.truncated());
        assertEquals(ChatAttachmentLimits.MAX_EXTRACT_CODE_POINTS, extracted.text().codePointCount(0, extracted.text().length()));
    }

    @Test
    void rejectsUnknownTypes() {
        assertThrows(RuntimeException.class, () -> ChatAttachmentExtractor.fileTypeOf("notes.exe"));
        assertThrows(RuntimeException.class, () -> ChatAttachmentExtractor.fileTypeOf("noext"));
    }

    @Test
    void promptCatalogOmitsBodiesAndStripRestoresTheUserQuery() {
        var resume = new com.jd.genie.platform.conversation.entity.ConversationAttachmentEntity();
        resume.setFileName("我的简历.pdf");
        resume.setFileType("pdf");
        resume.setExtractedText("曾组建数据分析团队并生成周报");
        ChatAttachmentPrompt.Prompts prompts = ChatAttachmentPrompt.prompts("分析简历", java.util.List.of(resume));
        assertTrue(prompts.routingQuery().contains("我的简历.pdf"));
        assertFalse(prompts.routingQuery().contains("组建数据分析团队"));
        assertTrue(prompts.specialistQuery().contains("组建数据分析团队"));
        assertEquals("分析简历", ChatAttachmentPrompt.withoutUploadedFileBodies(prompts.specialistQuery()));
    }

    @Test
    void promptKeepsFileOrderAndWrapsQuery() {
        var first = new com.jd.genie.platform.conversation.entity.ConversationAttachmentEntity();
        first.setFileName("a.md");
        first.setFileType("md");
        first.setExtractedText("AAA");
        var second = new com.jd.genie.platform.conversation.entity.ConversationAttachmentEntity();
        second.setFileName("b.csv");
        second.setFileType("csv");
        second.setExtractedText("BBB");
        String prompt = ChatAttachmentPrompt.enrich("请总结", java.util.List.of(first, second));
        assertTrue(prompt.startsWith("请总结"));
        assertTrue(prompt.contains("a.md"));
        assertTrue(prompt.contains("AAA"));
        assertTrue(prompt.indexOf("AAA") < prompt.indexOf("BBB"));
        assertFalse(prompt.contains("<script>"));
    }

    private static byte[] pdfBytes(String text) throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(PDType1Font.HELVETICA, 12);
                stream.newLineAtOffset(72, 720);
                stream.showText(text);
                stream.endText();
            }
            document.save(out);
            return out.toByteArray();
        }
    }

    private static byte[] docxBytes(String text) throws Exception {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XWPFParagraph paragraph = document.createParagraph();
            XWPFRun run = paragraph.createRun();
            run.setText(text);
            document.write(out);
            return out.toByteArray();
        }
    }
}
