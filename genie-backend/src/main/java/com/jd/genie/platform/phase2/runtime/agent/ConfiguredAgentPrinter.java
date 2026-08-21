package com.jd.genie.platform.phase2.runtime.agent;

import com.jd.genie.agent.dto.CodeInterpreterResponse;
import com.jd.genie.agent.dto.DataAnalysisResponse;
import com.jd.genie.agent.dto.DeepSearchrResponse;
import com.jd.genie.agent.enums.AgentType;
import com.jd.genie.agent.printer.Printer;
import com.jd.genie.model.response.GptProcessResult;
import com.jd.genie.platform.agentbridge.ConversationStreamObserver;
import com.jd.genie.platform.phase2.runtime.trace.OrchestrationTraceChannel;
import com.jd.genie.platform.phase2contract.BrowserSkillExecutionContract;
import com.jd.genie.platform.phase2contract.dto.BrowserSkillExecutionSignal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Printer for configured orchestration agents.
 * Optionally forwards safe progress (tool_thought plus search/code/analysis
 * status) onto the live trace channel. Does not dump search reports or frozen
 * result JSON as thinking text.
 * Transparently forwards frozen browser skill execution signals as skill_execution control packets.
 */
public final class ConfiguredAgentPrinter implements Printer {
    private static final Set<String> SAFE_THOUGHT_TYPES = Set.of("tool_thought");
    private static final Set<String> PROGRESS_TYPES = Set.of(
            "deep_search", "code_interpreter", "data_analysis", "code", "file");
    private static final String REPORT_PROGRESS = "正在根据网页整理要点";

    private final AtomicInteger progressCount = new AtomicInteger();
    private final AtomicInteger reportAnnounced = new AtomicInteger();
    private final OrchestrationTraceChannel traceChannel;
    private final ConversationStreamObserver observer;
    private final Integer attemptNo;
    private final String stepId;
    private final String agentId;
    private final String agentName;
    private final String subTaskId;
    private String streamingThoughtMessageId;
    private boolean streamingThoughtActive;
    private boolean streamingThoughtSuppressed;
    private final StringBuilder recoveredThought = new StringBuilder();

    public ConfiguredAgentPrinter() {
        this(null, null, null, null, null, null, null);
    }

    public ConfiguredAgentPrinter(
            OrchestrationTraceChannel traceChannel,
            ConversationStreamObserver observer,
            Integer attemptNo,
            String stepId,
            String agentId,
            String agentName
    ) {
        this(traceChannel, observer, attemptNo, stepId, agentId, agentName, null);
    }

    public ConfiguredAgentPrinter(
            OrchestrationTraceChannel traceChannel,
            ConversationStreamObserver observer,
            Integer attemptNo,
            String stepId,
            String agentId,
            String agentName,
            String subTaskId
    ) {
        this.traceChannel = traceChannel;
        this.observer = observer;
        this.attemptNo = attemptNo;
        this.stepId = stepId;
        this.agentId = agentId;
        this.agentName = agentName;
        this.subTaskId = subTaskId;
    }

    public int progressCount() {
        return progressCount.get();
    }

    /**
     * Human thought streamed before a missing SUCCESS envelope. Used to recover
     * AGENT_INVALID_RESULT when the model answered in markdown instead of JSON.
     */
    public String recoveredOutput() {
        String text = recoveredThought.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        StringBuilder useful = new StringBuilder();
        for (String line : text.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || isCannedProgress(trimmed)) {
                continue;
            }
            if (useful.length() > 0) {
                useful.append('\n');
            }
            useful.append(trimmed);
        }
        if (useful.length() < 24) {
            return null;
        }
        String recovered = useful.toString();
        return recovered.length() > 20_000 ? recovered.substring(0, 20_000) : recovered;
    }

    static boolean isCannedProgress(String text) {
        if (text == null || text.isBlank()) {
            return true;
        }
        if (text.startsWith("准备") && text.length() < 48) {
            return true;
        }
        if (text.startsWith("正在") && text.length() < 48) {
            return true;
        }
        return "代码执行完成".equals(text)
                || "数据分析完成".equals(text)
                || "搜索整理完成".equals(text);
    }

    @Override
    public void send(String messageId, String messageType, Object message, String digitalEmployee, Boolean isFinal) {
        progressCount.incrementAndGet();
        if (!forwardBrowserSkillSignal(messageType, message)) {
            forwardThought(messageId, messageType, message, isFinal);
        }
    }

    @Override
    public void send(String messageType, Object message) {
        progressCount.incrementAndGet();
        if (!forwardBrowserSkillSignal(messageType, message)) {
            forwardThought(null, messageType, message, Boolean.TRUE);
        }
    }

    @Override
    public void send(String messageType, Object message, String digitalEmployee) {
        progressCount.incrementAndGet();
        if (!forwardBrowserSkillSignal(messageType, message)) {
            forwardThought(null, messageType, message, Boolean.TRUE);
        }
    }

    @Override
    public void send(String messageId, String messageType, Object message, Boolean isFinal) {
        progressCount.incrementAndGet();
        if (!forwardBrowserSkillSignal(messageType, message)) {
            forwardThought(messageId, messageType, message, isFinal);
        }
    }

    @Override
    public void close() {
    }

    @Override
    public void updateAgentType(AgentType agentType) {
    }

    private boolean forwardBrowserSkillSignal(String messageType, Object message) {
        if (observer == null
                || !BrowserSkillExecutionContract.PRINTER_MESSAGE_TYPE.equals(messageType)
                || !(message instanceof BrowserSkillExecutionSignal signal)) {
            return false;
        }
        GptProcessResult packet = GptProcessResult.builder()
                .status("running")
                .response("")
                .responseAll("")
                .finished(false)
                .packageType(BrowserSkillExecutionContract.SSE_PACKAGE_TYPE)
                .resultMap(Map.of(BrowserSkillExecutionContract.RESULT_MAP_KEY, signal))
                .build();
        observer.onEvent(packet);
        return true;
    }

    private void forwardThought(String messageId, String messageType, Object message, Boolean isFinal) {
        if (traceChannel == null || messageType == null) {
            return;
        }
        if (PROGRESS_TYPES.contains(messageType)) {
            String progress = formatToolProgress(messageType, message);
            if (progress == null || progress.isBlank()) {
                return;
            }
            if (REPORT_PROGRESS.equals(progress) && reportAnnounced.getAndIncrement() > 0) {
                return;
            }
            emitTrace(OrchestrationTraceChannel.KIND_STATUS, progress, false);
            return;
        }
        if (!SAFE_THOUGHT_TYPES.contains(messageType)) {
            return;
        }
        String text = extractSafeText(message);
        if (text == null || text.isBlank()) {
            return;
        }
        if (streamingThoughtSuppressed && sameThoughtStream(messageId) && !Boolean.TRUE.equals(isFinal)) {
            return;
        }
        // ReactImplAgent streams the frozen SUCCESS/FAILURE JSON as tool_thought.
        // Deltas arrive as fragments (`","errorCode`), so suppress from the first
        // JSON token onward instead of waiting for a complete object.
        String visible = humanThoughtPrefix(text);
        if (visible == null) {
            streamingThoughtSuppressed = true;
            if (Boolean.TRUE.equals(isFinal)) {
                resetThoughtStream();
            }
            return;
        }
        boolean jsonTail = visible.length() < text.length();
        if (Boolean.FALSE.equals(isFinal)) {
            emitThoughtDelta(messageId, visible);
            if (jsonTail) {
                streamingThoughtSuppressed = true;
            }
            return;
        }
        // isFinal=true is a cumulative snapshot after streamed deltas. Emitting it
        // again would duplicate / overwrite the thinking view.
        if (streamingThoughtActive && sameThoughtStream(messageId)) {
            resetThoughtStream();
            return;
        }
        if (streamingThoughtSuppressed && sameThoughtStream(messageId)) {
            resetThoughtStream();
            return;
        }
        resetThoughtStream();
        rememberThought(visible, false);
        emitTrace(OrchestrationTraceChannel.KIND_THOUGHT, visible, false);
    }

    private void emitThoughtDelta(String messageId, String text) {
        if (streamingThoughtSuppressed && sameThoughtStream(messageId)) {
            return;
        }
        boolean append = streamingThoughtActive && sameThoughtStream(messageId);
        streamingThoughtMessageId = messageId;
        streamingThoughtActive = true;
        streamingThoughtSuppressed = false;
        rememberThought(text, append);
        emitTrace(OrchestrationTraceChannel.KIND_THOUGHT, text, append);
    }

    private void emitTrace(String kind, String text, boolean append) {
        if (traceChannel == null) {
            return;
        }
        if (subTaskId != null && !subTaskId.isBlank()) {
            traceChannel.emitSubTask(
                    attemptNo, null, stepId, subTaskId, agentId, agentName, kind, text, append);
            return;
        }
        traceChannel.emitStep(attemptNo, stepId, agentId, agentName, kind, text, append);
    }

    private void rememberThought(String text, boolean append) {
        if (text == null || text.isBlank()) {
            return;
        }
        if (recoveredThought.length() > 0 && !append) {
            recoveredThought.append('\n');
        }
        recoveredThought.append(text);
    }

    private boolean sameThoughtStream(String messageId) {
        if (messageId == null || streamingThoughtMessageId == null) {
            return streamingThoughtActive;
        }
        return messageId.equals(streamingThoughtMessageId);
    }

    private void resetThoughtStream() {
        streamingThoughtActive = false;
        streamingThoughtSuppressed = false;
        streamingThoughtMessageId = null;
    }

    /**
     * Human-readable prefix before a result-contract JSON object/fragment.
     * {@code null} means the whole chunk should be hidden.
     */
    static String humanThoughtPrefix(String text) {
        if (text == null) {
            return null;
        }
        int brace = text.indexOf('{');
        if (brace >= 0) {
            String fromBrace = text.substring(brace);
            if (looksLikeJsonObject(fromBrace) || looksLikeResultContract(fromBrace) || looksLikeResultJsonFragment(fromBrace)) {
                String prefix = text.substring(0, brace).trim();
                return prefix.isEmpty() ? null : prefix;
            }
        }
        if (looksLikeResultContract(text) || looksLikeJsonObject(text) || looksLikeResultJsonFragment(text)) {
            return null;
        }
        return text;
    }

    static boolean looksLikeResultJsonFragment(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String trimmed = text.trim();
        if (trimmed.contains("\"errorCode\"") || trimmed.contains("\"retryable\"")) {
            return true;
        }
        if (trimmed.contains("\"status\":\"SUCCESS\"") || trimmed.contains("\"status\":\"FAILURE\"")) {
            return true;
        }
        return trimmed.contains("errorCode") && (trimmed.contains("retryable") || trimmed.contains("\":\"") || trimmed.contains("\",\""));
    }

    static boolean looksLikeJsonObject(String text) {
        if (text == null) {
            return false;
        }
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            int nl = trimmed.indexOf('\n');
            if (nl > 0) {
                trimmed = trimmed.substring(nl + 1).trim();
            }
        }
        return trimmed.startsWith("{") && trimmed.contains("\"");
    }

    static String formatToolProgress(String messageType, Object message) {
        if ("deep_search".equals(messageType)) {
            return formatDeepSearchProgress(message);
        }
        if ("code".equals(messageType) || "code_interpreter".equals(messageType)) {
            if (message instanceof CodeInterpreterResponse response && Boolean.TRUE.equals(response.getIsFinal())) {
                return "代码执行完成";
            }
            return "正在运行代码";
        }
        if ("data_analysis".equals(messageType)) {
            if (message instanceof DataAnalysisResponse response && Boolean.TRUE.equals(response.getIsFinal())) {
                return "数据分析完成";
            }
            return "正在分析数据";
        }
        if ("file".equals(messageType)) {
            return "正在处理文件";
        }
        if (message instanceof Map<?, ?> map) {
            Object query = map.get("query");
            if (query instanceof CharSequence sequence && !sequence.toString().isBlank()) {
                return "正在使用工具 " + messageType + "：" + truncate(sequence.toString(), 80);
            }
        }
        return "正在使用工具 " + messageType;
    }

    static String formatDeepSearchProgress(Object message) {
        if (message instanceof Map<?, ?> map) {
            String type = stringOf(map.get("messageType"));
            String query = stringOf(map.get("query"));
            if ("start".equals(type)) {
                if (query != null && !query.isBlank()) {
                    return "正在联网搜索：" + truncate(query, 80);
                }
                return "正在联网搜索";
            }
        }
        if (message instanceof DeepSearchrResponse response) {
            if (Boolean.TRUE.equals(response.getIsFinal())) {
                return "搜索整理完成";
            }
            String type = response.getMessageType();
            if ("extend".equals(type)) {
                List<String> queries = response.getSearchResult() == null
                        ? List.of()
                        : response.getSearchResult().getQuery();
                if (queries != null && !queries.isEmpty()) {
                    return "搜索方向：" + String.join("；", queries.stream().limit(3).toList());
                }
                return "正在拆分搜索方向";
            }
            if ("search".equals(type)) {
                List<String> titles = firstTitles(response, 3);
                int count = countDocs(response);
                if (!titles.isEmpty()) {
                    return "已找到网页（" + count + "）：" + String.join("、", titles);
                }
                return "正在抓取网页";
            }
            if ("report".equals(type)) {
                return REPORT_PROGRESS;
            }
        }
        return null;
    }

    private static List<String> firstTitles(DeepSearchrResponse response, int limit) {
        if (response.getSearchResult() == null || response.getSearchResult().getDocs() == null) {
            return List.of();
        }
        List<String> titles = new ArrayList<>();
        for (List<DeepSearchrResponse.SearchDoc> group : response.getSearchResult().getDocs()) {
            if (group == null) {
                continue;
            }
            for (DeepSearchrResponse.SearchDoc doc : group) {
                if (doc == null || doc.getTitle() == null || doc.getTitle().isBlank()) {
                    continue;
                }
                titles.add(truncate(doc.getTitle(), 24));
                if (titles.size() >= limit) {
                    return titles;
                }
            }
        }
        return titles;
    }

    private static int countDocs(DeepSearchrResponse response) {
        if (response.getSearchResult() == null || response.getSearchResult().getDocs() == null) {
            return 0;
        }
        int count = 0;
        for (List<DeepSearchrResponse.SearchDoc> group : response.getSearchResult().getDocs()) {
            if (group != null) {
                count += group.size();
            }
        }
        return count;
    }

    private static String stringOf(Object value) {
        return value instanceof CharSequence sequence ? sequence.toString() : null;
    }

    private static String truncate(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        String trimmed = text.replaceAll("\\s+", " ").trim();
        if (trimmed.length() <= maxChars) {
            return trimmed;
        }
        return trimmed.substring(0, maxChars) + "…";
    }

    static boolean looksLikeResultContract(String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            int nl = trimmed.indexOf('\n');
            if (nl > 0) {
                trimmed = trimmed.substring(nl + 1).trim();
            }
        }
        if (!trimmed.startsWith("{")) {
            return false;
        }
        return trimmed.contains("\"status\"")
                && (trimmed.contains("\"SUCCESS\"") || trimmed.contains("\"FAILURE\""))
                && trimmed.contains("\"output\"")
                && trimmed.contains("\"errorCode\"")
                && trimmed.contains("\"retryable\"");
    }

    private static String extractSafeText(Object message) {
        if (message == null) {
            return null;
        }
        if (message instanceof CharSequence sequence) {
            return sequence.toString();
        }
        if (message instanceof Map<?, ?> map) {
            Object content = map.get("content");
            if (content instanceof CharSequence sequence) {
                return sequence.toString();
            }
            Object text = map.get("text");
            if (text instanceof CharSequence sequence) {
                return sequence.toString();
            }
            return null;
        }
        return message.toString();
    }
}
