package com.jd.genie.platform.phase2.memory.store;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2.configuration.memory.dto.MemoryPatchItem;
import com.jd.genie.platform.phase2.configuration.memory.validation.MemoryPatchValidator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class MemoryMarkdownDocuments {
    static final int LTM_MAX_CODEPOINTS = 12_000;
    static final int SUMMARY_MAX_CODEPOINTS = 20_000;
    static final int SUMMARY_SECTION_MAX_CODEPOINTS = 5_000;
    static final int LOCAL_CONTEXT_MAX_CODEPOINTS = 30_000;

    static final List<String> LONG_TERM_SECTIONS = List.of(
        MemoryPatchValidator.SECTION_BASIC_INFO,
        MemoryPatchValidator.SECTION_ANSWER_PREFERENCE,
        MemoryPatchValidator.SECTION_LONG_TERM_GOAL,
        MemoryPatchValidator.SECTION_LONG_TERM_CONSTRAINT
    );

    static final List<String> SUMMARY_SECTIONS = List.of("当前目标", "已确认事实", "已完成内容", "未解决事项");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MemoryMarkdownDocuments() {
    }

    static LongTermDoc emptyLongTerm(String updatedAt) {
        Map<String, List<Entry>> sections = new LinkedHashMap<>();
        for (String name : LONG_TERM_SECTIONS) {
            sections.put(name, new ArrayList<>());
        }
        return new LongTermDoc(1, updatedAt, sections);
    }

    static String serializeLongTerm(LongTermDoc doc) {
        StringBuilder out = new StringBuilder();
        out.append("---\n");
        out.append("schemaVersion: ").append(doc.schemaVersion()).append('\n');
        out.append("updatedAt: ").append(doc.updatedAt()).append('\n');
        out.append("---\n\n");
        for (String section : LONG_TERM_SECTIONS) {
            out.append("## ").append(section).append('\n');
            List<Entry> entries = new ArrayList<>(doc.sections().getOrDefault(section, List.of()));
            entries.sort((a, b) -> a.key().compareTo(b.key()));
            for (Entry entry : entries) {
                out.append("- ").append(jsonEntry(entry)).append('\n');
            }
            out.append('\n');
        }
        return trimTrailingBlankLines(out.toString()) + "\n";
    }

    static String serializeSummary(SummaryDoc doc) {
        StringBuilder out = new StringBuilder();
        out.append("---\n");
        out.append("schemaVersion: ").append(doc.schemaVersion()).append('\n');
        out.append("conversationId: ").append(doc.conversationId()).append('\n');
        out.append("lastSummarizedTurnNo: ").append(doc.lastSummarizedTurnNo()).append('\n');
        out.append("updatedAt: ").append(doc.updatedAt()).append('\n');
        out.append("---\n\n");
        for (String section : SUMMARY_SECTIONS) {
            out.append("## ").append(section).append('\n');
            String body = doc.sections().getOrDefault(section, "");
            if (!body.isEmpty()) {
                out.append(body).append('\n');
            }
            out.append('\n');
        }
        return trimTrailingBlankLines(out.toString()) + "\n";
    }

    static ParseResult<LongTermDoc> parseLongTerm(String raw) {
        if (raw == null) {
            return ParseResult.fail("missing body");
        }
        if (hasControlChars(raw)) {
            return ParseResult.fail("control characters");
        }
        if (codePoints(raw) > LTM_MAX_CODEPOINTS) {
            return ParseResult.fail("long-term memory too large");
        }
        Split split = splitFrontMatter(raw);
        if (split == null) {
            return ParseResult.fail("missing front matter");
        }
        if (!"1".equals(split.meta.get("schemaVersion"))) {
            return ParseResult.fail("invalid schemaVersion");
        }
        String updatedAt = split.meta.get("updatedAt");
        if (updatedAt == null || !isIsoInstant(updatedAt)) {
            return ParseResult.fail("invalid updatedAt");
        }
        Map<String, String> sectionsMap = parseSections(split.body);
        if (sectionsMap == null) {
            return ParseResult.fail("invalid sections layout");
        }
        if (sectionsMap.size() != LONG_TERM_SECTIONS.size()) {
            return ParseResult.fail("section count mismatch");
        }
        Map<String, List<Entry>> sections = new LinkedHashMap<>();
        for (String name : LONG_TERM_SECTIONS) {
            if (!sectionsMap.containsKey(name)) {
                return ParseResult.fail("missing section " + name);
            }
            List<Entry> entries = parseEntries(sectionsMap.get(name));
            if (entries == null) {
                return ParseResult.fail("invalid entries in " + name);
            }
            sections.put(name, entries);
        }
        for (String name : sectionsMap.keySet()) {
            if (!LONG_TERM_SECTIONS.contains(name)) {
                return ParseResult.fail("unexpected section " + name);
            }
        }
        return ParseResult.ok(new LongTermDoc(1, updatedAt, sections));
    }

    static ParseResult<SummaryDoc> parseSummary(String raw) {
        if (raw == null) {
            return ParseResult.fail("missing body");
        }
        if (hasControlChars(raw)) {
            return ParseResult.fail("control characters");
        }
        if (codePoints(raw) > SUMMARY_MAX_CODEPOINTS) {
            return ParseResult.fail("summary too large");
        }
        Split split = splitFrontMatter(raw);
        if (split == null) {
            return ParseResult.fail("missing front matter");
        }
        if (!"1".equals(split.meta.get("schemaVersion"))) {
            return ParseResult.fail("invalid schemaVersion");
        }
        String conversationId = split.meta.get("conversationId");
        if (conversationId == null || conversationId.isBlank()) {
            return ParseResult.fail("missing conversationId");
        }
        String turnRaw = split.meta.get("lastSummarizedTurnNo");
        long turnNo;
        try {
            turnNo = Long.parseLong(turnRaw);
            if (turnNo < 0) {
                return ParseResult.fail("invalid lastSummarizedTurnNo");
            }
        } catch (Exception ex) {
            return ParseResult.fail("invalid lastSummarizedTurnNo");
        }
        String updatedAt = split.meta.get("updatedAt");
        if (updatedAt == null || !isIsoInstant(updatedAt)) {
            return ParseResult.fail("invalid updatedAt");
        }
        Map<String, String> sectionsMap = parseSections(split.body);
        if (sectionsMap == null) {
            return ParseResult.fail("invalid sections layout");
        }
        ParseResult<Map<String, String>> sections = requireSummarySections(sectionsMap);
        if (!sections.ok) {
            return ParseResult.fail(sections.reason);
        }
        return ParseResult.ok(new SummaryDoc(1, conversationId, turnNo, updatedAt, sections.doc));
    }

    static ParseResult<Map<String, String>> parseSummarySections(String markdown) {
        if (markdown == null) {
            return ParseResult.fail("missing body");
        }
        if (hasControlChars(markdown)) {
            return ParseResult.fail("control characters");
        }
        Map<String, String> sectionsMap = parseSections(markdown.replace("\r", ""));
        if (sectionsMap == null) {
            return ParseResult.fail("invalid sections layout");
        }
        return requireSummarySections(sectionsMap);
    }

    static LongTermDoc applyPatches(LongTermDoc doc, List<MemoryPatchItem> patches) {
        Map<String, List<Entry>> next = new LinkedHashMap<>();
        for (String name : LONG_TERM_SECTIONS) {
            next.put(name, new ArrayList<>(doc.sections().getOrDefault(name, List.of())));
        }
        for (MemoryPatchItem patch : patches) {
            List<Entry> list = next.get(patch.section());
            if (list == null) {
                continue;
            }
            int idx = indexOfKey(list, patch.key());
            if ("DELETE".equals(patch.operation())) {
                if (idx >= 0) {
                    list.remove(idx);
                }
                continue;
            }
            Entry entry = new Entry(patch.key(), patch.value() == null ? "" : patch.value());
            if (idx >= 0) {
                list.set(idx, entry);
            } else {
                list.add(entry);
            }
        }
        return new LongTermDoc(1, Instant.now().toString(), next);
    }

    static String clipForQuery(String value, int maxCodePoints) {
        if (value == null) {
            return "";
        }
        if (codePoints(value) <= maxCodePoints) {
            return value;
        }
        return "";
    }

    static int codePoints(String value) {
        return value == null ? 0 : value.codePointCount(0, value.length());
    }

    private static ParseResult<Map<String, String>> requireSummarySections(Map<String, String> sectionsMap) {
        if (sectionsMap.size() != SUMMARY_SECTIONS.size()) {
            return ParseResult.fail("section count mismatch");
        }
        Map<String, String> sections = new LinkedHashMap<>();
        for (String name : SUMMARY_SECTIONS) {
            if (!sectionsMap.containsKey(name)) {
                return ParseResult.fail("missing section " + name);
            }
            String text = sectionsMap.get(name);
            if (codePoints(text) > SUMMARY_SECTION_MAX_CODEPOINTS) {
                return ParseResult.fail("section too large: " + name);
            }
            sections.put(name, text);
        }
        for (String name : sectionsMap.keySet()) {
            if (!SUMMARY_SECTIONS.contains(name)) {
                return ParseResult.fail("unexpected section " + name);
            }
        }
        return ParseResult.ok(sections);
    }

    private static List<Entry> parseEntries(String sectionBody) {
        if (sectionBody == null || sectionBody.isBlank()) {
            return new ArrayList<>();
        }
        List<Entry> entries = new ArrayList<>();
        Set<String> seen = new java.util.HashSet<>();
        for (String line : sectionBody.split("\n", -1)) {
            if (line.isBlank()) {
                continue;
            }
            if (!line.startsWith("- ")) {
                return null;
            }
            JsonNode parsed;
            try {
                parsed = MAPPER.readTree(line.substring(2));
            } catch (Exception ex) {
                return null;
            }
            if (parsed == null || !parsed.isObject()
                || !parsed.has("key") || !parsed.get("key").isTextual()
                || !parsed.has("value") || !parsed.get("value").isTextual()) {
                return null;
            }
            String key = parsed.get("key").asText();
            if (!seen.add(key)) {
                return null;
            }
            entries.add(new Entry(key, parsed.get("value").asText()));
        }
        return entries;
    }

    private static Split splitFrontMatter(String raw) {
        String normalized = raw.startsWith("\uFEFF") ? raw.substring(1) : raw;
        if (!normalized.startsWith("---\n") && !normalized.startsWith("---\r\n")) {
            return null;
        }
        int end = normalized.indexOf("\n---", 3);
        if (end < 0) {
            return null;
        }
        String fmBlock = normalized.substring(4, end).replace("\r", "");
        int bodyStart = end + "\n---".length();
        if (bodyStart < normalized.length() && normalized.charAt(bodyStart) == '\r') {
            bodyStart += 1;
        }
        if (bodyStart < normalized.length() && normalized.charAt(bodyStart) == '\n') {
            bodyStart += 1;
        }
        Map<String, String> meta = new LinkedHashMap<>();
        for (String line : fmBlock.split("\n", -1)) {
            if (line.isBlank()) {
                continue;
            }
            int idx = line.indexOf(':');
            if (idx <= 0) {
                return null;
            }
            meta.put(line.substring(0, idx).trim(), line.substring(idx + 1).trim());
        }
        return new Split(meta, normalized.substring(bodyStart).replace("\r", ""));
    }

    private static Map<String, String> parseSections(String body) {
        String[] lines = body.split("\n", -1);
        Map<String, List<String>> sections = new LinkedHashMap<>();
        String current = null;
        for (String line : lines) {
            if (line.startsWith("## ")) {
                current = line.substring(3).trim();
                sections.putIfAbsent(current, new ArrayList<>());
                continue;
            }
            if (current == null) {
                if (line.isBlank()) {
                    continue;
                }
                return null;
            }
            sections.get(current).add(line);
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : sections.entrySet()) {
            List<String> contentLines = entry.getValue();
            int end = contentLines.size();
            while (end > 0 && contentLines.get(end - 1).isEmpty()) {
                end -= 1;
            }
            int start = 0;
            while (start < end && contentLines.get(start).isEmpty()) {
                start += 1;
            }
            result.put(entry.getKey(), String.join("\n", contentLines.subList(start, end)));
        }
        return result;
    }

    private static int indexOfKey(List<Entry> list, String key) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).key().equals(key)) {
                return i;
            }
        }
        return -1;
    }

    private static String jsonEntry(Entry entry) {
        try {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("key", entry.key());
            node.put("value", entry.value());
            return MAPPER.writeValueAsString(node);
        } catch (Exception ex) {
            throw new MemoryStoreException(MvpErrorCode.INTERNAL_ERROR, "serialize entry failed", ex);
        }
    }

    private static boolean hasControlChars(String text) {
        for (int i = 0; i < text.length(); i++) {
            char code = text.charAt(i);
            if (code == 0x7f || (code <= 0x1f && code != 0x09 && code != 0x0a && code != 0x0d)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isIsoInstant(String value) {
        try {
            Instant.parse(value);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private static String trimTrailingBlankLines(String value) {
        return value.replaceAll("\\n+$", "");
    }

    record Entry(String key, String value) {
    }

    record LongTermDoc(int schemaVersion, String updatedAt, Map<String, List<Entry>> sections) {
    }

    record SummaryDoc(
        int schemaVersion,
        String conversationId,
        long lastSummarizedTurnNo,
        String updatedAt,
        Map<String, String> sections
    ) {
    }

    static final class ParseResult<T> {
        final boolean ok;
        final T doc;
        final String reason;

        private ParseResult(boolean ok, T doc, String reason) {
            this.ok = ok;
            this.doc = doc;
            this.reason = reason;
        }

        static <T> ParseResult<T> ok(T doc) {
            return new ParseResult<>(true, doc, null);
        }

        static <T> ParseResult<T> fail(String reason) {
            return new ParseResult<>(false, null, reason);
        }
    }

    private record Split(Map<String, String> meta, String body) {
    }
}
