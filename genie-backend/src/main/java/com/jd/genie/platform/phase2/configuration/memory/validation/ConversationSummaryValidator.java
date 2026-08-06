package com.jd.genie.platform.phase2.configuration.memory.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2.configuration.memory.dto.ConversationSummaryResponse;
import com.jd.genie.platform.phase2.configuration.memory.exception.MemoryAnalysisException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class ConversationSummaryValidator {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_MARKDOWN_CODE_POINTS = 20_000;
    public static final String TITLE = "\u5f53\u524d\u5bf9\u8bdd\u6458\u8981";
    public static final String SECTION_CURRENT_GOAL = "\u5f53\u524d\u76ee\u6807";
    public static final String SECTION_CONFIRMED_FACTS = "\u5df2\u786e\u8ba4\u4e8b\u5b9e";
    public static final String SECTION_COMPLETED = "\u5df2\u5b8c\u6210\u5185\u5bb9";
    public static final String SECTION_OPEN_ITEMS = "\u672a\u89e3\u51b3\u4e8b\u9879";

    private static final Set<String> TOP_LEVEL_FIELDS = Set.of("schemaVersion", "markdown");
    private static final List<String> REQUIRED_SECTIONS = List.of(
        SECTION_CURRENT_GOAL,
        SECTION_CONFIRMED_FACTS,
        SECTION_COMPLETED,
        SECTION_OPEN_ITEMS
    );
    private static final Pattern H2_PATTERN = Pattern.compile("(?m)^##\\s+(.+?)\\s*$");
    private static final Pattern DANGEROUS_LINK = Pattern.compile("(?i)\\]\\((?:javascript|data|vbscript):");
    private static final Pattern HTML_EVENT = Pattern.compile("(?i)<[^>]+\\son\\w+\\s*=");
    private static final Pattern PATH_TRAVERSAL = Pattern.compile("(^|[\\\\/])\\.\\.([\\\\/]|$)");

    private final ObjectMapper objectMapper;
    private final MemorySecretFilter secretFilter;

    public ConversationSummaryValidator(MemorySecretFilter secretFilter) {
        this.objectMapper = new ObjectMapper();
        this.secretFilter = secretFilter;
    }

    public ConversationSummaryResponse parseAndValidate(String rawContent) {
        if (blank(rawContent) || looksWrapped(rawContent)) {
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
        requireOnlyFields(root);
        JsonNode schemaVersion = root.get("schemaVersion");
        JsonNode markdown = root.get("markdown");
        if (schemaVersion == null || !schemaVersion.isInt() || schemaVersion.asInt() != SCHEMA_VERSION
            || markdown == null || !markdown.isTextual()) {
            throw failed();
        }
        return validateMarkdown(markdown.asText());
    }

    public ConversationSummaryResponse validateMarkdown(String markdown) {
        if (blank(markdown) || codePoints(markdown) > MAX_MARKDOWN_CODE_POINTS || unsafeMarkdown(markdown)) {
            throw failed();
        }
        List<String> headings = new ArrayList<>();
        var matcher = H2_PATTERN.matcher(markdown);
        while (matcher.find()) {
            headings.add(matcher.group(1).trim());
        }
        if (!headings.equals(REQUIRED_SECTIONS)) {
            throw failed();
        }
        String withoutOptionalTitle = markdown.trim();
        if (withoutOptionalTitle.startsWith("# ")) {
            String firstLine = withoutOptionalTitle.lines().findFirst().orElse("");
            if (!("# " + TITLE).equals(firstLine.trim())) {
                throw failed();
            }
        }
        return new ConversationSummaryResponse(SCHEMA_VERSION, markdown.trim());
    }

    private boolean unsafeMarkdown(String value) {
        String trimmed = value.trim();
        return trimmed.startsWith("---")
            || trimmed.contains("```")
            || trimmed.contains("<script")
            || HTML_EVENT.matcher(value).find()
            || DANGEROUS_LINK.matcher(value).find()
            || PATH_TRAVERSAL.matcher(value).find()
            || Pattern.compile("(?im)^\\s*(schemaVersion|updatedAt)\\s*[:=]").matcher(value).find()
            || secretFilter.containsSecret(value);
    }

    private void requireOnlyFields(JsonNode objectNode) {
        Iterator<String> fields = objectNode.fieldNames();
        int count = 0;
        while (fields.hasNext()) {
            count++;
            if (!TOP_LEVEL_FIELDS.contains(fields.next())) {
                throw failed();
            }
        }
        if (count != TOP_LEVEL_FIELDS.size()) {
            throw failed();
        }
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
        return new MemoryAnalysisException(MvpErrorCode.SUMMARY_FAILED, MvpErrorCode.SUMMARY_FAILED.name());
    }
}
