package com.jd.genie.platform.phase2.memory.store;

import com.jd.genie.platform.conversation.entity.ConversationEntity;
import com.jd.genie.platform.conversation.mapper.ConversationMapper;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2.configuration.memory.dto.MemoryPatchResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class MemoryDocumentService {
    private final MemoryDiskStore store;
    private final ConversationMapper conversationMapper;

    public MemoryDocumentService(MemoryDiskStore store) {
        this.store = store;
        this.conversationMapper = null;
    }

    MemoryDocumentService(MemoryDiskStore store, ConversationMapper conversationMapper) {
        this.store = store;
        this.conversationMapper = conversationMapper;
    }

    @Autowired
    public MemoryDocumentService(MemoryDiskStore store, ObjectProvider<ConversationMapper> conversationMapper) {
        this(store, conversationMapper == null ? null : conversationMapper.getIfAvailable());
    }

    public boolean isAvailable() {
        return store.isAvailable();
    }

    public String rootPath() {
        return store.root().toString();
    }

    public MemoryFileSnapshot readLongTerm(String userId) {
        return read(store::readLongTerm, userId, MemoryMarkdownDocuments::parseLongTerm);
    }

    public MemoryFileSnapshot readSummary(String userId, String conversationId) {
        if (!store.isAvailable()) {
            return MemoryFileSnapshot.unavailable();
        }
        try {
            String raw = store.readSummary(userId, conversationId);
            return snapshotOf(raw, MemoryMarkdownDocuments::parseSummary);
        } catch (MemoryStoreException ex) {
            if (ex.code() == MvpErrorCode.VALIDATION_ERROR) {
                throw ex;
            }
            return MemoryFileSnapshot.unavailable();
        }
    }

    public void writeLongTerm(String userId, String markdown) {
        var parsed = MemoryMarkdownDocuments.parseLongTerm(markdown);
        if (!parsed.ok) {
            throw new MemoryStoreException(MvpErrorCode.VALIDATION_ERROR, parsed.reason);
        }
        String serialized = MemoryMarkdownDocuments.serializeLongTerm(parsed.doc);
        if (MemoryMarkdownDocuments.codePoints(serialized) > MemoryMarkdownDocuments.LTM_MAX_CODEPOINTS) {
            throw new MemoryStoreException(MvpErrorCode.VALIDATION_ERROR, "long-term memory too large");
        }
        store.writeLongTerm(userId, serialized);
    }

    public void writeSummary(String userId, String conversationId, String markdown) {
        var parsed = MemoryMarkdownDocuments.parseSummary(markdown);
        if (!parsed.ok) {
            throw new MemoryStoreException(MvpErrorCode.VALIDATION_ERROR, parsed.reason);
        }
        if (!conversationId.equals(parsed.doc.conversationId())) {
            throw new MemoryStoreException(MvpErrorCode.VALIDATION_ERROR, "conversationId mismatch");
        }
        String serialized = MemoryMarkdownDocuments.serializeSummary(parsed.doc);
        if (MemoryMarkdownDocuments.codePoints(serialized) > MemoryMarkdownDocuments.SUMMARY_MAX_CODEPOINTS) {
            throw new MemoryStoreException(MvpErrorCode.VALIDATION_ERROR, "summary too large");
        }
        store.writeSummary(userId, conversationId, serialized);
    }

    public void deleteLongTerm(String userId) {
        store.deleteLongTerm(userId);
    }

    public void deleteSummary(String userId, String conversationId) {
        store.deleteSummary(userId, conversationId);
    }

    public List<MemorySummaryIndexItem> listSummaries(String userId) {
        if (!store.isAvailable()) {
            throw new MemoryStoreException(MvpErrorCode.INTERNAL_ERROR, "memory store unavailable");
        }
        List<MemorySummaryIndexItem> items = new ArrayList<>();
        for (String conversationId : store.listSummaryConversationIds(userId)) {
            MemoryFileSnapshot snapshot = readSummary(userId, conversationId);
            String updatedAt = Instant.EPOCH.toString();
            Long lastTurn = null;
            if (snapshot.status() == MemoryFileSnapshot.Status.READY && snapshot.markdown() != null) {
                var parsed = MemoryMarkdownDocuments.parseSummary(snapshot.markdown());
                if (parsed.ok) {
                    updatedAt = parsed.doc.updatedAt();
                    lastTurn = parsed.doc.lastSummarizedTurnNo();
                }
            } else if (snapshot.status() == MemoryFileSnapshot.Status.CORRUPTED) {
                updatedAt = Instant.now().toString();
            }
            items.add(new MemorySummaryIndexItem(
                conversationId,
                "/memory/v1/users/" + userId + "/conversations/" + conversationId + "/对话摘要.md",
                updatedAt,
                lastTurn
            ));
        }
        return List.copyOf(items);
    }

    public void persistAnalyzeResult(String userId, MemoryPatchResponse patches) {
        if (patches == null || patches.patches() == null || patches.patches().isEmpty()) {
            return;
        }
        MemoryFileSnapshot current = readLongTerm(userId);
        if (current.status() == MemoryFileSnapshot.Status.UNAVAILABLE) {
            throw new MemoryStoreException(MvpErrorCode.INTERNAL_ERROR, "memory store unavailable");
        }
        if (current.status() == MemoryFileSnapshot.Status.CORRUPTED) {
            return;
        }
        MemoryMarkdownDocuments.LongTermDoc doc;
        if (current.status() == MemoryFileSnapshot.Status.READY) {
            var parsed = MemoryMarkdownDocuments.parseLongTerm(current.markdown());
            if (!parsed.ok) {
                return;
            }
            doc = parsed.doc;
        } else {
            doc = MemoryMarkdownDocuments.emptyLongTerm(Instant.now().toString());
        }
        MemoryMarkdownDocuments.LongTermDoc next = MemoryMarkdownDocuments.applyPatches(doc, patches.patches());
        store.writeLongTerm(userId, MemoryMarkdownDocuments.serializeLongTerm(next));
    }

    public void persistSummaryMarkdown(
        String userId,
        String conversationId,
        String sectionMarkdown,
        long lastSummarizedTurnNo
    ) {
        MemoryFileSnapshot current = readSummary(userId, conversationId);
        if (current.status() == MemoryFileSnapshot.Status.UNAVAILABLE) {
            throw new MemoryStoreException(MvpErrorCode.INTERNAL_ERROR, "memory store unavailable");
        }
        if (current.status() == MemoryFileSnapshot.Status.CORRUPTED) {
            return;
        }
        var sections = MemoryMarkdownDocuments.parseSummarySections(sectionMarkdown);
        if (!sections.ok) {
            throw new MemoryStoreException(MvpErrorCode.VALIDATION_ERROR, sections.reason);
        }
        var doc = new MemoryMarkdownDocuments.SummaryDoc(
            1,
            conversationId,
            lastSummarizedTurnNo,
            Instant.now().toString(),
            sections.doc
        );
        store.writeSummary(userId, conversationId, MemoryMarkdownDocuments.serializeSummary(doc));
    }

    public long lastSummarizedTurnNo(String userId, String conversationId) {
        MemoryFileSnapshot snapshot = readSummary(userId, conversationId);
        if (snapshot.status() != MemoryFileSnapshot.Status.READY || snapshot.markdown() == null) {
            return 0L;
        }
        var parsed = MemoryMarkdownDocuments.parseSummary(snapshot.markdown());
        return parsed.ok ? parsed.doc.lastSummarizedTurnNo() : 0L;
    }

    public LocalMemorySnapshot loadForQuery(String userId, String conversationId) {
        return loadForQuery(null, userId, conversationId);
    }

    public LocalMemorySnapshot loadForQuery(String tenantId, String userId, String conversationId) {
        if (!store.isAvailable()) {
            return LocalMemorySnapshot.empty();
        }
        if (isPrivacyConversation(tenantId, userId, conversationId)) {
            return LocalMemorySnapshot.empty();
        }
        String longTerm = safeReadyMarkdown(readLongTerm(userId));
        String summary = conversationId == null || conversationId.isBlank()
            ? ""
            : safeReadyMarkdown(readSummary(userId, conversationId));
        longTerm = MemoryMarkdownDocuments.clipForQuery(longTerm, MemoryMarkdownDocuments.LTM_MAX_CODEPOINTS);
        summary = MemoryMarkdownDocuments.clipForQuery(summary, MemoryMarkdownDocuments.SUMMARY_MAX_CODEPOINTS);
        if (MemoryMarkdownDocuments.codePoints(longTerm) + MemoryMarkdownDocuments.codePoints(summary)
            > MemoryMarkdownDocuments.LOCAL_CONTEXT_MAX_CODEPOINTS) {
            summary = "";
        }
        if (MemoryMarkdownDocuments.codePoints(longTerm) + MemoryMarkdownDocuments.codePoints(summary)
            > MemoryMarkdownDocuments.LOCAL_CONTEXT_MAX_CODEPOINTS) {
            return LocalMemorySnapshot.empty();
        }
        return new LocalMemorySnapshot(longTerm, summary);
    }

    private boolean isPrivacyConversation(String tenantId, String userId, String conversationId) {
        if (conversationMapper == null || tenantId == null || tenantId.isBlank()
            || userId == null || conversationId == null || conversationId.isBlank()) {
            return false;
        }
        ConversationEntity conversation = conversationMapper.selectOwnedConversation(
            tenantId, userId, conversationId);
        return conversation != null && Boolean.TRUE.equals(conversation.getPrivacyMode());
    }

    private MemoryFileSnapshot read(
        java.util.function.Function<String, String> reader,
        String userId,
        java.util.function.Function<String, MemoryMarkdownDocuments.ParseResult<?>> parser
    ) {
        if (!store.isAvailable()) {
            return MemoryFileSnapshot.unavailable();
        }
        try {
            return snapshotOf(reader.apply(userId), parser);
        } catch (MemoryStoreException ex) {
            if (ex.code() == MvpErrorCode.VALIDATION_ERROR) {
                throw ex;
            }
            return MemoryFileSnapshot.unavailable();
        }
    }

    private MemoryFileSnapshot snapshotOf(
        String raw,
        java.util.function.Function<String, MemoryMarkdownDocuments.ParseResult<?>> parser
    ) {
        if (raw == null) {
            return MemoryFileSnapshot.empty();
        }
        var parsed = parser.apply(raw);
        if (!parsed.ok) {
            return MemoryFileSnapshot.corrupted(raw, parsed.reason);
        }
        return MemoryFileSnapshot.ready(raw);
    }

    private String safeReadyMarkdown(MemoryFileSnapshot snapshot) {
        if (snapshot.status() == MemoryFileSnapshot.Status.READY && snapshot.markdown() != null) {
            return snapshot.markdown();
        }
        return "";
    }

    public record MemorySummaryIndexItem(
        String conversationId,
        String path,
        String updatedAt,
        Long lastSummarizedTurnNo
    ) {
    }
}
