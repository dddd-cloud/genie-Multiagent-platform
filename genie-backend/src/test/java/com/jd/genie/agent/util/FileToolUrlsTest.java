package com.jd.genie.agent.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileToolUrlsTest {
    @Test
    void rewritesNonePrefixedToolUrls() {
        String url = FileToolUrls.publicUrl(
                "None/download/step-1/自我介绍.html",
                "http://127.0.0.1:1601",
                "download",
                "step-1",
                "自我介绍.html"
        );
        assertEquals("http://127.0.0.1:1601/v1/file_tool/download/step-1/自我介绍.html", url);
    }

    @Test
    void keepsAbsoluteHttpUrls() {
        String url = FileToolUrls.publicUrl(
                "http://127.0.0.1:1601/v1/file_tool/preview/r/page.html",
                "http://127.0.0.1:1601",
                "preview",
                "r",
                "page.html"
        );
        assertEquals("http://127.0.0.1:1601/v1/file_tool/preview/r/page.html", url);
        assertTrue(FileToolUrls.isHttpUrl(url));
        assertFalse(FileToolUrls.isHttpUrl("None/download/x"));
    }
}
