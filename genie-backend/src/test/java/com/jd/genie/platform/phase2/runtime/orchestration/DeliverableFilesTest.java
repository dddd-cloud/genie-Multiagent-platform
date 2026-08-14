package com.jd.genie.platform.phase2.runtime.orchestration;

import com.jd.genie.agent.dto.File;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeliverableFilesTest {
    @Test
    void skipsInternalFilesAndAppendsMarkdownLinks() {
        List<File> sink = new ArrayList<>();
        DeliverableFiles.collect(sink, List.of(
            File.builder().fileName("secret.txt").ossUrl("http://x/secret").isInternalFile(true).build(),
            File.builder().fileName("page.html").ossUrl("http://127.0.0.1:1601/v1/file_tool/download/r/page.html")
                .domainUrl("http://127.0.0.1:1601/v1/file_tool/preview/r/page.html").fileSize(12).build()
        ));
        assertEquals(1, sink.size());
        List<Map<String, Object>> fileList = DeliverableFiles.toFileList(sink);
        assertEquals("page.html", fileList.get(0).get("fileName"));
        String answer = DeliverableFiles.appendDownloadLinks("已按品牌规范生成页面。", sink);
        assertTrue(answer.contains("[page.html]("));
        assertTrue(answer.contains("/download/r/page.html"));
    }

    @Test
    void doesNotCollectTheSameDownloadUrlTwice() {
        File file = File.builder().fileName("page.html").ossUrl("http://x/page.html").build();
        List<File> sink = new ArrayList<>();
        DeliverableFiles.collect(sink, List.of(file, file));
        assertEquals(1, sink.size());
    }

    @Test
    void doesNotDuplicateExistingUrls() {
        File file = File.builder().fileName("note.md").ossUrl("http://x/note.md").build();
        String answer = DeliverableFiles.appendDownloadLinks("看 [note.md](http://x/note.md)", List.of(file));
        assertEquals("看 [note.md](http://x/note.md)", answer);
    }
}
