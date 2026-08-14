package com.jd.genie.agent.util;

import com.jd.genie.agent.dto.File;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileUtilTest {

    @Test
    void formatFileNamesOmitsDescriptions() {
        File file = File.builder()
                .fileName("东南亚市场规模.md")
                .description("a".repeat(1500))
                .ossUrl("http://example/file.md")
                .isInternalFile(false)
                .build();

        String rendered = FileUtil.formatFileNames(List.of(file), true);

        assertTrue(rendered.contains("东南亚市场规模.md"));
        assertTrue(rendered.contains("http://example/file.md"));
        assertFalse(rendered.contains("fileDesc:"));
        assertFalse(rendered.contains("aaa"));
    }

    @Test
    void formatFileNamesSkipsInternalFilesAndNullList() {
        File internal = File.builder()
                .fileName("secret.md")
                .description("hidden")
                .isInternalFile(true)
                .build();

        assertEquals("", FileUtil.formatFileNames(null, true));
        assertEquals("", FileUtil.formatFileNames(List.of(internal), true));
    }
}
