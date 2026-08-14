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

    public ConfiguredAgentPrinter() {
        this(null, null, null, null, null, null);
    }

    public ConfiguredAgentPrinter(
            OrchestrationTraceChannel traceChannel,
            ConversationStreamObserver observer,
            Integer attemptNo,
            String stepId,
            String agentId,
            String agentName
    ) {
        this.traceChannel = traceChannel;
        this.observer = observer;
        this.attemptNo = attemptNo;
        this.stepId = stepId;
        this.agentId = agentId;
        this.agentName = agentName;
    }

    public int progressCount() {
        return progressCount.get();
    }

    @Override
    public void send(String messageId, String messageType, Object message, String digitalEmployee, Boolean isFinal) {
        progressCount.incrementAndGet();
        if (!forwardBrowserSkillSignal(messageType, message)) {
            forwardThought(messageType, message);
        }
    }

    @Override
    public void send(String messageType, Object message) {
        progressCount.incrementAndGet();
        if (!forwardBrowserSkillSignal(messageType, message)) {
            forwardThought(messageType, message);
        }
    }

    @Override
    public void send(String messageType, Object message, String digitalEmployee) {
        progressCount.incrementAndGet();
        if (!forwardBrowserSkillSignal(messageType, message)) {
            forwardThought(messageType, message);
        }
    }

    @Override
    public void send(String messageId, String messageType, Object message, Boolean isFinal) {
        progressCount.incrementAndGet();
        if (!forwardBrowserSkillSignal(messageType, message)) {
            forwardThought(messageType, message);
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

    private void forwardThought(String messageType, Object message) {
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
            traceChannel.emitStep(attemptNo, stepId, agentId, agentName,
                    OrchestrationTraceChannel.KIND_STATUS, progress, false);
            return;
        }
        if (!SAFE_THOUGHT_TYPES.contains(messageType)) {
            return;
        }
        String text = extractSafeText(message);
        if (text == null || text.isBlank()) {
            return;
        }
        // ReactImplAgent forwards the final model content as tool_thought when not streaming.
        // That content is usually the frozen result JSON — never show it as "thinking".
        if (looksLikeResultContract(text)) {
            return;
        }
        traceChannel.emitStep(attemptNo, stepId, agentId, agentName,
                OrchestrationTraceChannel.KIND_THOUGHT, text, false);
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
