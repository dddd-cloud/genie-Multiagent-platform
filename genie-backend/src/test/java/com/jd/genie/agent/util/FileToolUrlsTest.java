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

    @Test
    void prefixesRelativeV1FileToolPaths() {
        String url = FileToolUrls.publicUrl(
                "/v1/file_tool/download/sub-2/贪吃蛇前端_类型定义.ts",
                "http://genie-tool:1601",
                "download",
                "sub-2",
                "贪吃蛇前端_类型定义.ts"
        );
        assertEquals(
                "http://genie-tool:1601/v1/file_tool/download/sub-2/贪吃蛇前端_类型定义.ts",
                url
        );
    }

    @Test
    void extractsUploadScopeFromPublicUrls() {
        assertEquals(
                "sub-2",
                FileToolUrls.scopeFromUrl("http://genie-tool:1601/v1/file_tool/download/sub-2/贪吃蛇前端_类型定义.ts")
        );
        assertEquals(
                "step-2",
                FileToolUrls.scopeFromUrl("/v1/file_tool/preview/step-2/page.html")
        );
        assertEquals(null, FileToolUrls.scopeFromUrl("https://example.com/other"));
    }
}
