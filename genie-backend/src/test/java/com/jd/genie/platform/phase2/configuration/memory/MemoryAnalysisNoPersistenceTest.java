package com.jd.genie.platform.phase2.configuration.memory;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryAnalysisNoPersistenceTest {

    @Test
    void productionMemoryModuleDoesNotDependOnPersistenceOrConversationStorage() throws Exception {
        Path root = Path.of("src/main/java/com/jd/genie/platform/phase2/configuration/memory");
        List<Path> files = Files.walk(root)
            .filter(Files::isRegularFile)
            .filter(path -> path.toString().endsWith(".java"))
            .toList();

        String combined = new StringBuilder()
            .append('\n')
            .append(String.join("\n", files.stream().map(path -> {
                try {
                    return Files.readString(path);
                } catch (Exception ex) {
                    throw new IllegalStateException(ex);
                }
            }).toList()))
            .toString();

        assertFalse(combined.contains("JdbcTemplate"));
        assertFalse(combined.contains("Repository"));
        assertFalse(combined.contains("@Mapper"));
        assertFalse(combined.contains("ConversationMapper"));
        assertFalse(combined.contains("conversation_message"));
        assertFalse(combined.contains("conversation/"));
        assertFalse(combined.contains("FileWriter"));
        assertFalse(combined.contains("Files.write"));
        assertFalse(combined.contains("static Map"));
        assertFalse(combined.contains("ThreadLocal"));
        assertFalse(combined.contains("log.info"));
        assertTrue(files.stream().anyMatch(path -> path.endsWith("MemoryAnalysisService.java")));
        assertTrue(files.stream().anyMatch(path -> path.endsWith("ConversationSummaryAnalysisService.java")));
    }
}
