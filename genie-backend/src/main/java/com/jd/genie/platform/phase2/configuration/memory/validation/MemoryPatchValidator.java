package com.jd.genie.platform.phase2.configuration.memory.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2.configuration.memory.dto.MemoryPatchItem;
import com.jd.genie.platform.phase2.configuration.memory.dto.MemoryPatchResponse;
import com.jd.genie.platform.phase2.configuration.memory.exception.MemoryAnalysisException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class MemoryPatchValidator {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_PATCH_COUNT = 20;
    public static final int MAX_RESPONSE_CODE_POINTS = 20_000;
    public static final int MAX_KEY_CODE_POINTS = 64;
    public static final int MAX_VALUE_CODE_POINTS = 2_000;

    public static final String SECTION_BASIC_INFO = "\u57fa\u672c\u4fe1\u606f";
    public static final String SECTION_ANSWER_PREFERENCE = "\u56de\u7b54\u504f\u597d";
    public static final String SECTION_LONG_TERM_GOAL = "\u957f\u671f\u76ee\u6807";
    public static final String SECTION_LONG_TERM_CONSTRAINT = "\u957f\u671f\u7ea6\u675f";

    private static final Set<String> TOP_LEVEL_FIELDS = Set.of("schemaVersion", "patches");
    private static final Set<String> PATCH_FIELDS = Set.of("operation", "section", "key", "value");
    private static final Set<String> OPERATIONS = Set.of("UPSERT", "DELETE");
    private static final Set<String> SECTIONS = Set.of(
        SECTION_BASIC_INFO,
        SECTION_ANSWER_PREFERENCE,
        SECTION_LONG_TERM_GOAL,
        SECTION_LONG_TERM_CONSTRAINT
    );
    private static final Pattern KEY_PATTERN = Pattern.compile("^[\\p{L}\\p{N}_.-]{1,64}$");

    private final ObjectMapper objectMapper;
    private final MemorySecretFilter secretFilter;
    private final MemoryMarkdownGuard markdownGuard;

    public MemoryPatchValidator(MemorySecretFilter secretFilter, MemoryMarkdownGuard markdownGuard) {
        this.objectMapper = new ObjectMapper();
        this.secretFilter = secretFilter;
        this.markdownGuard = markdownGuard;
    }

    /**
     * Model output is often wrapped in markdown or carries extra keys.
     * Strip those, then apply the frozen patch schema.
     */
    public MemoryPatchResponse parseModelOutput(String rawContent) {
        return parseAndValidate(sanitizeModelJson(rawContent));
    }

    public MemoryPatchResponse parseAndValidate(String rawContent) {
        if (blank(rawContent) || codePoints(rawContent) > MAX_RESPONSE_CODE_POINTS || looksWrapped(rawContent)) {
            throw failed();
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(rawContent);
        } catch (Exception ex) {
            throw failed();
        }
        if (root == null || !root.isObject()) {
            throw failed();
        }
        requireOnlyFields(root, TOP_LEVEL_FIELDS);
        JsonNode schemaVersion = root.get("schemaVersion");
        JsonNode patches = root.get("patches");
        if (schemaVersion == null || !schemaVersion.isInt() || schemaVersion.asInt() != SCHEMA_VERSION
            || patches == null || !patches.isArray() || patches.size() > MAX_PATCH_COUNT) {
            throw failed();
        }
        List<MemoryPatchItem> items = new ArrayList<>();
        Set<String> uniqueKeys = new HashSet<>();
        for (JsonNode patch : patches) {
            items.add(validatePatch(patch, uniqueKeys));
        }
        return new MemoryPatchResponse(SCHEMA_VERSION, items);
    }

    private MemoryPatchItem validatePatch(JsonNode patch, Set<String> uniqueKeys) {
        if (patch == null || !patch.isObject()) {
            throw failed();
        }
        requireOnlyFields(patch, PATCH_FIELDS);
        JsonNode operationNode = patch.get("operation");
        JsonNode sectionNode = patch.get("section");
        JsonNode keyNode = patch.get("key");
        JsonNode valueNode = patch.get("value");
        if (!textNode(operationNode) || !textNode(sectionNode) || !textNode(keyNode) || valueNode == null) {
            throw failed();
        }
        String operation = operationNode.asText();
        String section = sectionNode.asText();
        String key = keyNode.asText().trim();
        if (!OPERATIONS.contains(operation) || !SECTIONS.contains(section) || !validKey(key)) {
            throw failed();
        }
        if (!uniqueKeys.add(section + "\u0000" + key)) {
            throw failed();
        }
        if (secretFilter.containsSecret(section) || secretFilter.containsSecret(key)) {
            throw failed();
        }
        if ("DELETE".equals(operation)) {
            if (!valueNode.isNull()) {
                throw failed();
            }
            return new MemoryPatchItem(operation, section, key, null);
        }
        if (!textNode(valueNode)) {
            throw failed();
        }
        String value = valueNode.asText().trim();
        if (blank(value) || codePoints(value) > MAX_VALUE_CODE_POINTS
            || secretFilter.containsSecret(value) || markdownGuard.isUnsafe(value)) {
            throw failed();
        }
        return new MemoryPatchItem(operation, section, key, value);
    }

    private void requireOnlyFields(JsonNode objectNode, Set<String> allowed) {
        Iterator<String> fields = objectNode.fieldNames();
        int count = 0;
        while (fields.hasNext()) {
            count++;
            if (!allowed.contains(fields.next())) {
                throw failed();
            }
        }
        if (count != allowed.size()) {
            throw failed();
        }
    }

    private boolean validKey(String key) {
        return !blank(key) && codePoints(key) <= MAX_KEY_CODE_POINTS && KEY_PATTERN.matcher(key).matches()
            && !secretFilter.containsSecret(key) && !markdownGuard.isUnsafe(key);
    }

    private boolean textNode(JsonNode node) {
        return node != null && node.isTextual();
    }

    private String sanitizeModelJson(String rawContent) {
        if (blank(rawContent) || codePoints(rawContent) > MAX_RESPONSE_CODE_POINTS) {
            throw failed();
        }
        try {
            JsonNode root = objectMapper.readTree(extractJsonObject(rawContent));
            if (root == null || !root.isObject()) {
                throw failed();
            }
            var sanitized = objectMapper.createObjectNode();
            JsonNode schemaVersion = root.get("schemaVersion");
            if (schemaVersion != null && schemaVersion.isNumber()) {
                sanitized.put("schemaVersion", schemaVersion.asInt());
            } else if (schemaVersion != null) {
                sanitized.set("schemaVersion", schemaVersion);
            }
            var patches = objectMapper.createArrayNode();
            JsonNode rawPatches = root.get("patches");
            if (rawPatches != null && rawPatches.isArray()) {
                for (JsonNode patch : rawPatches) {
                    if (patch == null || !patch.isObject()) {
                        continue;
                    }
                    var item = objectMapper.createObjectNode();
                    for (String field : PATCH_FIELDS) {
                        if (patch.has(field)) {
                            item.set(field, patch.get(field));
                        }
                    }
                    patches.add(item);
                }
            }
            sanitized.set("patches", patches);
            return objectMapper.writeValueAsString(sanitized);
        } catch (MemoryAnalysisException ex) {
            throw ex;
        } catch (Exception ex) {
            throw failed();
        }
    }

    private String extractJsonObject(String rawContent) {
        String trimmed = rawContent.trim();
        if (trimmed.startsWith("```")) {
            int firstNl = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNl > 0 && lastFence > firstNl) {
                trimmed = trimmed.substring(firstNl + 1, lastFence).trim();
            }
        }
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    private boolean looksWrapped(String value) {
        String trimmed = value.trim();
        return trimmed.startsWith("```") || trimmed.endsWith("```") || !trimmed.startsWith("{") || !trimmed.endsWith("}");
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private int codePoints(String value) {
        return value == null ? 0 : value.codePointCount(0, value.length());
    }

    private MemoryAnalysisException failed() {
        return new MemoryAnalysisException(MvpErrorCode.MEMORY_ANALYSIS_FAILED, MvpErrorCode.MEMORY_ANALYSIS_FAILED.name());
    }
}
