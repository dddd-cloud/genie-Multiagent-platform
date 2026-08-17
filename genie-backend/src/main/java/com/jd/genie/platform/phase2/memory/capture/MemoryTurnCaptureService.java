package com.jd.genie.platform.phase2.memory.capture;

import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.conversation.entity.ConversationEntity;
import com.jd.genie.platform.conversation.entity.ConversationMessageEntity;
import com.jd.genie.platform.conversation.mapper.ConversationMapper;
import com.jd.genie.platform.conversation.mapper.ConversationMessageMapper;
import com.jd.genie.platform.phase2.configuration.memory.dto.ConversationSummaryAnalysisRequest;
import com.jd.genie.platform.phase2.configuration.memory.dto.ConversationSummaryResponse;
import com.jd.genie.platform.phase2.configuration.memory.dto.ConversationSummaryTurn;
import com.jd.genie.platform.phase2.configuration.memory.dto.MemoryAnalysisRequest;
import com.jd.genie.platform.phase2.configuration.memory.dto.MemoryPatchResponse;
import com.jd.genie.platform.phase2.configuration.memory.service.ConversationSummaryAnalysisService;
import com.jd.genie.platform.phase2.configuration.memory.service.MemoryAnalysisService;
import com.jd.genie.platform.phase2.memory.store.MemoryDocumentService;
import com.jd.genie.platform.phase2.memory.store.MemoryFileSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * After an assistant turn reaches COMPLETED, persist long-term patches and
 * maybe a conversation summary on disk. Never throws into the chat path.
 */
@Slf4j
@Service
public class MemoryTurnCaptureService {
    static final int SUMMARIZE_TURN_DELTA = 5;
    static final int LOCAL_CONTEXT_WARN_CODEPOINTS = 27_000;

    private final ConversationMapper conversationMapper;
    private final ConversationMessageMapper messageMapper;
    private final MemoryAnalysisService analysisService;
    private final ConversationSummaryAnalysisService summaryService;
    private final MemoryDocumentService documents;
    private final Executor executor;

    @Autowired
    public MemoryTurnCaptureService(
        ConversationMapper conversationMapper,
        ConversationMessageMapper messageMapper,
        MemoryAnalysisService analysisService,
        ConversationSummaryAnalysisService summaryService,
        MemoryDocumentService documents
    ) {
        this(
            conversationMapper,
            messageMapper,
            analysisService,
            summaryService,
            documents,
            daemonExecutor()
        );
    }

    MemoryTurnCaptureService(
        ConversationMapper conversationMapper,
        ConversationMessageMapper messageMapper,
        MemoryAnalysisService analysisService,
        ConversationSummaryAnalysisService summaryService,
        MemoryDocumentService documents,
        Executor executor
    ) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.analysisService = analysisService;
        this.summaryService = summaryService;
        this.documents = documents;
        this.executor = executor == null ? Runnable::run : executor;
    }

    public void scheduleAfterComplete(CurrentUser user, String assistantMessageId) {
        runAfterCommit(() -> executor.execute(() -> captureQuietly(user, assistantMessageId)));
    }

    void captureQuietly(CurrentUser user, String assistantMessageId) {
        try {
            capture(user, assistantMessageId);
        } catch (Exception ex) {
            log.warn(
                "Memory capture skipped, userId={}, assistantMessageId={}",
                user == null ? null : user.userId(),
                assistantMessageId,
                ex
            );
        }
    }

    void capture(CurrentUser user, String assistantMessageId) {
        if (user == null || assistantMessageId == null || assistantMessageId.isBlank()) {
            return;
        }
        if (!documents.isAvailable()) {
            return;
        }
        ConversationMessageEntity assistant = messageMapper.selectOwnedMessage(
            user.tenantId(), user.userId(), assistantMessageId);
        if (assistant == null || !"ASSISTANT".equals(assistant.getRole()) || !"COMPLETED".equals(assistant.getStatus())) {
            return;
        }
        ConversationEntity conversation = conversationMapper.selectOwnedConversation(
            user.tenantId(), user.userId(), assistant.getConversationId());
        if (conversation == null || Boolean.TRUE.equals(conversation.getPrivacyMode())) {
            return;
        }
        ConversationMessageEntity userMessage = messageMapper.selectOwnedMessageByRequestRole(
            user.tenantId(),
            user.userId(),
            assistant.getConversationId(),
            assistant.getRequestId(),
            "USER"
        );
        if (userMessage == null) {
            return;
        }
        try {
            analyzeTurn(user.userId(), assistant.getConversationId(), userMessage, assistant);
        } catch (Exception ex) {
            log.warn(
                "Long-term memory analyze skipped, userId={}, conversationId={}",
                user.userId(),
                assistant.getConversationId(),
                ex
            );
        }
        try {
            maybeSummarize(user, assistant.getConversationId());
        } catch (Exception ex) {
            log.warn(
                "Conversation summary skipped, userId={}, conversationId={}",
                user.userId(),
                assistant.getConversationId(),
                ex
            );
        }
    }

    private void analyzeTurn(
        String userId,
        String conversationId,
        ConversationMessageEntity userMessage,
        ConversationMessageEntity assistant
    ) {
        MemoryFileSnapshot current = documents.readLongTerm(userId);
        if (current.status() == MemoryFileSnapshot.Status.CORRUPTED
            || current.status() == MemoryFileSnapshot.Status.UNAVAILABLE) {
            return;
        }
        String currentMemory = current.status() == MemoryFileSnapshot.Status.READY ? current.markdown() : "";
        MemoryPatchResponse patches = analysisService.analyzeTurn(new MemoryAnalysisRequest(
            conversationId,
            nullToEmpty(userMessage.getContent()),
            nullToEmpty(assistant.getContent()),
            currentMemory,
            "COMPLETED"
        ));
        documents.persistAnalyzeResult(userId, patches);
    }

    private void maybeSummarize(CurrentUser user, String conversationId) {
        List<ConversationMessageEntity> messages = messageMapper.selectMessagesByOwnedConversation(
            user.tenantId(), user.userId(), conversationId);
        List<CompletedTurn> turns = completedTurns(messages);
        if (turns.isEmpty()) {
            return;
        }
        MemoryFileSnapshot summary = documents.readSummary(user.userId(), conversationId);
        if (summary.status() == MemoryFileSnapshot.Status.CORRUPTED
            || summary.status() == MemoryFileSnapshot.Status.UNAVAILABLE) {
            return;
        }
        long lastSummarized = documents.lastSummarizedTurnNo(user.userId(), conversationId);
        long maxTurn = turns.get(turns.size() - 1).turnNo();
        boolean empty = summary.status() == MemoryFileSnapshot.Status.EMPTY;
        boolean due = maxTurn - lastSummarized >= SUMMARIZE_TURN_DELTA;
        boolean budget = overBudget(user.userId(), conversationId, summary);
        if (!empty && !due && !budget) {
            return;
        }
        List<CompletedTurn> newTurns = turns.stream()
            .filter(turn -> turn.turnNo() > lastSummarized)
            .toList();
        if (newTurns.isEmpty() && !empty) {
            return;
        }
        List<ConversationSummaryTurn> payload = (newTurns.isEmpty() ? turns : newTurns).stream()
            .map(turn -> new ConversationSummaryTurn(
                turn.turnNo(),
                turn.userContent(),
                turn.assistantContent(),
                "COMPLETED"
            ))
            .toList();
        String currentSummary = summary.status() == MemoryFileSnapshot.Status.READY ? summary.markdown() : "";
        ConversationSummaryResponse response = summaryService.summarize(
            new ConversationSummaryAnalysisRequest(conversationId, currentSummary, payload)
        );
        if (response == null || response.markdown() == null) {
            return;
        }
        documents.persistSummaryMarkdown(user.userId(), conversationId, response.markdown(), maxTurn);
    }

    private boolean overBudget(String userId, String conversationId, MemoryFileSnapshot summary) {
        MemoryFileSnapshot ltm = documents.readLongTerm(userId);
        String ltmText = ltm.status() == MemoryFileSnapshot.Status.READY ? nullToEmpty(ltm.markdown()) : "";
        String summaryText = summary.status() == MemoryFileSnapshot.Status.READY
            ? nullToEmpty(summary.markdown()) : "";
        return codePoints(ltmText) + codePoints(summaryText) >= LOCAL_CONTEXT_WARN_CODEPOINTS;
    }

    private static List<CompletedTurn> completedTurns(List<ConversationMessageEntity> messages) {
        List<ConversationMessageEntity> ordered = new ArrayList<>(messages);
        ordered.sort(Comparator
            .comparing(ConversationMessageEntity::getTurnNo, Comparator.nullsLast(Long::compareTo))
            .thenComparing(ConversationMessageEntity::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())));
        List<CompletedTurn> turns = new ArrayList<>();
        for (ConversationMessageEntity message : ordered) {
            if (!"USER".equals(message.getRole()) || !"COMPLETED".equals(message.getStatus())) {
                continue;
            }
            ConversationMessageEntity assistant = ordered.stream()
                .filter(item -> "ASSISTANT".equals(item.getRole())
                    && "COMPLETED".equals(item.getStatus())
                    && message.getRequestId().equals(item.getRequestId()))
                .findFirst()
                .orElse(null);
            if (assistant == null || message.getTurnNo() == null) {
                continue;
            }
            turns.add(new CompletedTurn(
                message.getTurnNo(),
                nullToEmpty(message.getContent()),
                nullToEmpty(assistant.getContent())
            ));
        }
        return turns;
    }

    private void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private static Executor daemonExecutor() {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "memory-turn-capture");
            thread.setDaemon(true);
            return thread;
        });
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static int codePoints(String value) {
        return value == null ? 0 : value.codePointCount(0, value.length());
    }

    private record CompletedTurn(long turnNo, String userContent, String assistantContent) {
    }
}
