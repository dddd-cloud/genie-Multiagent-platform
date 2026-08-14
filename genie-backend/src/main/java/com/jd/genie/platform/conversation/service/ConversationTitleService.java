package com.jd.genie.platform.conversation.service;

import com.jd.genie.platform.conversation.mapper.ConversationMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Service
public class ConversationTitleService {
    public static final String DEFAULT_TITLE = "新对话";
    static final int TITLE_CODE_POINTS = 9;

    private final ConversationMapper conversationMapper;
    private final ConversationTitleModelPort titleModelPort;
    private final Executor titleExecutor;

    public ConversationTitleService(ConversationMapper conversationMapper) {
        this(conversationMapper, null, Runnable::run);
    }

    @Autowired
    public ConversationTitleService(
            ConversationMapper conversationMapper,
            ObjectProvider<ConversationTitleModelPort> titleModelPort
    ) {
        this(conversationMapper, titleModelPort.getIfAvailable(), daemonTitleExecutor());
    }

    ConversationTitleService(
            ConversationMapper conversationMapper,
            ConversationTitleModelPort titleModelPort,
            Executor titleExecutor
    ) {
        this.conversationMapper = conversationMapper;
        this.titleModelPort = titleModelPort;
        this.titleExecutor = titleExecutor == null ? Runnable::run : titleExecutor;
    }

    public void autoTitleFirstTurn(String tenantId, String ownerId, String conversationId, long turnNo,
                                   String query, Instant updatedAt) {
        if (turnNo != 1) {
            return;
        }
        String fallback = generateTitle(query);
        conversationMapper.autoTitleFirstTurnIfDefault(
            tenantId,
            ownerId,
            conversationId,
            turnNo + 1,
            DEFAULT_TITLE,
            fallback,
            updatedAt
        );
        if (titleModelPort == null) {
            return;
        }
        runAfterCommit(() -> refineTitleWithModel(
                tenantId, ownerId, conversationId, turnNo, query, fallback));
    }

    public String generateTitle(String query) {
        return clampTitle(normalizeQuery(query), DEFAULT_TITLE);
    }

    static String sanitizeModelTitle(String raw) {
        if (raw == null) {
            return "";
        }
        String cleaned = raw.replace('\n', ' ').replace('\r', ' ').trim();
        if (cleaned.startsWith("```")) {
            int firstNl = cleaned.indexOf('\n');
            int lastFence = cleaned.lastIndexOf("```");
            if (firstNl > 0 && lastFence > firstNl) {
                cleaned = cleaned.substring(firstNl + 1, lastFence).trim();
            }
        }
        cleaned = cleaned.replaceAll("[\\p{P}\\p{S}]+", "").replaceAll("\\s+", "").trim();
        return clampTitle(cleaned, "");
    }

    private void refineTitleWithModel(
            String tenantId,
            String ownerId,
            String conversationId,
            long turnNo,
            String query,
            String fallback
    ) {
        try {
            String summarized = sanitizeModelTitle(titleModelPort.summarizeFirstQuery(query));
            if (summarized.isEmpty() || summarized.equals(fallback) || DEFAULT_TITLE.equals(summarized)) {
                return;
            }
            conversationMapper.autoTitleFirstTurnIfDefault(
                    tenantId,
                    ownerId,
                    conversationId,
                    turnNo + 1,
                    fallback,
                    summarized,
                    Instant.now()
            );
        } catch (Exception ex) {
            // Keep the fallback title; first-turn send must not fail because of titling.
        }
    }

    private void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    titleExecutor.execute(action);
                }
            });
        } else {
            titleExecutor.execute(action);
        }
    }

    private static String normalizeQuery(String query) {
        String normalized = query == null ? "" : query.replace('\n', ' ').replace('\r', ' ');
        return normalized.replaceAll("\\s+", " ").trim();
    }

    private static String clampTitle(String text, String emptyDefault) {
        if (text == null || text.isEmpty()) {
            return emptyDefault;
        }
        int codePoints = text.codePointCount(0, text.length());
        if (codePoints <= TITLE_CODE_POINTS) {
            return text;
        }
        return text.substring(0, text.offsetByCodePoints(0, TITLE_CODE_POINTS));
    }

    private static Executor daemonTitleExecutor() {
        return Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "conversation-title");
            thread.setDaemon(true);
            return thread;
        });
    }
}
