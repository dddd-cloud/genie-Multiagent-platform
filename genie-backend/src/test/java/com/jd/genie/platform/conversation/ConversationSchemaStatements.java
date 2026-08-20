package com.jd.genie.platform.conversation;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class ConversationSchemaStatements {
    private ConversationSchemaStatements() {
    }

    static List<String> load() {
        List<String> statements = new ArrayList<>();
        statements.addAll(split("db/migration/V003__conversation.sql"));
        statements.addAll(split("db/migration/V006__conversation_privacy.sql"));
        statements.addAll(split("db/migration/V010__conversation_attachment.sql"));
        return statements;
    }

    private static List<String> split(String path) {
        try (var input = Thread.currentThread().getContextClassLoader().getResourceAsStream(path)) {
            if (input == null) {
                throw new IllegalStateException("Missing resource: " + path);
            }
            return List.of(new String(input.readAllBytes(), StandardCharsets.UTF_8).split(";"))
                .stream()
                .map(String::trim)
                .filter(statement -> !statement.isEmpty())
                .toList();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read resource: " + path, e);
        }
    }
}
