package com.jd.genie.platform.phase2.memory.store;

import com.jd.genie.platform.contract.MvpErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Component
public class MemoryDiskStore {
    static final String LONG_TERM_FILE = "长期记忆.md";
    static final String SUMMARY_FILE = "对话摘要.md";

    private final Path root;

    @Autowired
    public MemoryDiskStore(@Value("${genie.memory.dir:${GENIE_MEMORY_DIR:}}") String configuredDir) {
        this(resolveRoot(configuredDir));
    }

    MemoryDiskStore(Path root) {
        this.root = root.toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.root);
        } catch (Exception ignored) {
            // Availability is re-checked on each read/write.
        }
    }

    public Path root() {
        return root;
    }

    public boolean isAvailable() {
        try {
            Files.createDirectories(root);
            return Files.isDirectory(root) && Files.isWritable(root);
        } catch (Exception ex) {
            return false;
        }
    }

    public String readLongTerm(String userId) {
        return read(longTermPath(userId));
    }

    public void writeLongTerm(String userId, String content) {
        writeVerified(longTermPath(userId), content);
    }

    public void deleteLongTerm(String userId) {
        delete(longTermPath(userId));
    }

    public String readSummary(String userId, String conversationId) {
        return read(summaryPath(userId, conversationId));
    }

    public void writeSummary(String userId, String conversationId, String content) {
        writeVerified(summaryPath(userId, conversationId), content);
    }

    public void deleteSummary(String userId, String conversationId) {
        delete(summaryPath(userId, conversationId));
    }

    public List<String> listSummaryConversationIds(String userId) {
        Path conversations = userRoot(userId).resolve("conversations");
        if (!Files.isDirectory(conversations)) {
            return List.of();
        }
        List<String> ids = new ArrayList<>();
        try (Stream<Path> stream = Files.list(conversations)) {
            stream.filter(Files::isDirectory).forEach(dir -> {
                Path file = dir.resolve(SUMMARY_FILE);
                if (Files.isRegularFile(file)) {
                    ids.add(dir.getFileName().toString());
                }
            });
        } catch (IOException ex) {
            throw storeFailed("list summaries failed", ex);
        }
        ids.sort(String::compareTo);
        return List.copyOf(ids);
    }

    public Path longTermPath(String userId) {
        return userRoot(userId).resolve(LONG_TERM_FILE);
    }

    public Path summaryPath(String userId, String conversationId) {
        return userRoot(userId)
            .resolve("conversations")
            .resolve(MemoryPathGuard.requireSegment(conversationId, "conversationId"))
            .resolve(SUMMARY_FILE);
    }

    private Path userRoot(String userId) {
        Path resolved = root.resolve("v1").resolve("users")
            .resolve(MemoryPathGuard.requireSegment(userId, "userId"))
            .normalize();
        if (!resolved.startsWith(root)) {
            throw new MemoryStoreException(MvpErrorCode.VALIDATION_ERROR, "Invalid userId");
        }
        return resolved;
    }

    private String read(Path path) {
        if (!isAvailable()) {
            throw new MemoryStoreException(MvpErrorCode.INTERNAL_ERROR, "memory store unavailable");
        }
        if (!Files.exists(path)) {
            return null;
        }
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw storeFailed("read failed", ex);
        }
    }

    private void writeVerified(Path path, String content) {
        if (!isAvailable()) {
            throw new MemoryStoreException(MvpErrorCode.INTERNAL_ERROR, "memory store unavailable");
        }
        try {
            Files.createDirectories(path.getParent());
            Path tmp = path.resolveSibling(path.getFileName().toString() + ".tmp");
            Files.writeString(
                tmp,
                content,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
            );
            try {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
            String readBack = Files.readString(path, StandardCharsets.UTF_8);
            if (!content.equals(readBack)) {
                throw new MemoryStoreException(MvpErrorCode.INTERNAL_ERROR, "memory write mismatch");
            }
        } catch (MemoryStoreException ex) {
            throw ex;
        } catch (IOException ex) {
            throw storeFailed("write failed", ex);
        }
    }

    private void delete(Path path) {
        if (!isAvailable()) {
            throw new MemoryStoreException(MvpErrorCode.INTERNAL_ERROR, "memory store unavailable");
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            throw storeFailed("delete failed", ex);
        }
    }

    private static Path resolveRoot(String configuredDir) {
        if (configuredDir != null && !configuredDir.isBlank()) {
            return Path.of(configuredDir).toAbsolutePath().normalize();
        }
        return Path.of(System.getProperty("user.home"), ".joyagent", "memory").toAbsolutePath().normalize();
    }

    private static MemoryStoreException storeFailed(String message, Exception ex) {
        return new MemoryStoreException(MvpErrorCode.INTERNAL_ERROR, message, ex);
    }
}
