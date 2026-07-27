package com.jd.genie.platform.agentbridge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.model.response.GptProcessResult;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.contract.StreamSnapshotEnvelope;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FinalAnswerExtractorTest {

    private static final Path SNAPSHOT_FIXTURES = Path.of("..", "docs", "mvp-contract", "fixtures", "snapshot");
    private static final Path SSE_FIXTURES = Path.of("..", "docs", "mvp-contract", "fixtures", "sse");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final FinalAnswerExtractor extractor = new FinalAnswerExtractor();

    @Test
    void extractsReactAndPlanAnswersFromFrozenSnapshotFixtures() throws Exception {
        assertEquals(
                "分析完成，这是 ReAct 模式的最终回答。",
                extractor.extract(readSnapshotFixture("react-success.json").events())
        );
        assertEquals(
                "Plan 模式分析完成，已生成完整报告。",
                extractor.extract(readSnapshotFixture("plan-success.json").events())
        );
    }

    @Test
    void extractsReactAndPlanAnswersFromRawSseFixtures() throws Exception {
        assertEquals(
                "分析完成，这是 ReAct 模式的最终回答。",
                extractor.extract(readSseFixture("success-react.ndjson"))
        );
        assertEquals(
                "Plan 模式分析完成，已生成完整报告。",
                extractor.extract(readSseFixture("success-plan.ndjson"))
        );
    }

    @Test
    void responseAllHasPriorityAcrossFinishedEvents() {
        List<GptProcessResult> events = List.of(
                event(true, "早期全量", "早期增量", null),
                event(true, "", "较晚增量", null)
        );

        assertEquals("早期全量", extractor.extract(events));
    }

    @Test
    void fallsBackToLastFinishedResponse() {
        List<GptProcessResult> events = List.of(
                event(true, "", "早期增量", null),
                event(true, " ", "最终增量", null)
        );

        assertEquals("最终增量", extractor.extract(events));
    }

    @Test
    void extractsTaskSummaryBeforeResultFromNestedLastEventData() {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("result", "候选结果");
        nested.put("taskSummary", "结构化总结");
        Map<String, Object> eventData = Map.of("resultMap", nested);

        assertEquals(
                "结构化总结",
                extractor.extract(List.of(event(false, "", "", Map.of("eventData", eventData))))
        );
    }

    @Test
    void onlyTheLastEventProvidesStructuredFallback() {
        GptProcessResult earlier = event(false, "", "", Map.of(
                "eventData", Map.of("taskSummary", "旧总结")
        ));
        GptProcessResult last = event(false, "", "", Map.of("eventData", Map.of()));

        AgentBridgeException exception = assertThrows(
                AgentBridgeException.class,
                () -> extractor.extract(List.of(earlier, last))
        );

        assertEquals(MvpErrorCode.AGENT_NO_FINAL_EVENT, exception.getErrorCode());
    }

    @Test
    void nullCollectionsAndNullEventsUseFrozenErrorCode() {
        AgentBridgeException nullCollection = assertThrows(
                AgentBridgeException.class,
                () -> extractor.extract(null)
        );
        List<GptProcessResult> eventsWithNull = new ArrayList<>();
        eventsWithNull.add(null);
        AgentBridgeException nullEvent = assertThrows(
                AgentBridgeException.class,
                () -> extractor.extract(eventsWithNull)
        );

        assertEquals(MvpErrorCode.AGENT_NO_FINAL_EVENT, nullCollection.getErrorCode());
        assertEquals(MvpErrorCode.AGENT_NO_FINAL_EVENT, nullEvent.getErrorCode());
    }

    @Test
    void traversesIterableAndArrayStructuredPayloads() {
        Map<String, Object> iterableEventData = Map.of(
                "items", List.of(
                        Map.of("ignored", "first"),
                        Map.of("taskSummary", "列表中的总结")
                )
        );
        Map<String, Object> arrayEventData = Map.of(
                "items", new Object[]{
                        Map.of("ignored", "first"),
                        Map.of("result", "数组中的结果")
                }
        );

        assertEquals(
                "列表中的总结",
                extractor.extract(List.of(event(false, "", "", Map.of("eventData", iterableEventData))))
        );
        assertEquals(
                "数组中的结果",
                extractor.extract(List.of(event(false, "", "", Map.of("eventData", arrayEventData))))
        );
    }

    @Test
    void ignoresNonTextAndBlankStructuredCandidates() {
        Map<String, Object> eventData = new LinkedHashMap<>();
        eventData.put("taskSummary", 42);
        eventData.put("result", "   ");

        AgentBridgeException exception = assertThrows(
                AgentBridgeException.class,
                () -> extractor.extract(List.of(event(false, "", "", Map.of("eventData", eventData))))
        );

        assertEquals(MvpErrorCode.AGENT_NO_FINAL_EVENT, exception.getErrorCode());
    }

    @Test
    void missingAnswerUsesFrozenErrorCode() {
        AgentBridgeException exception = assertThrows(
                AgentBridgeException.class,
                () -> extractor.extract(List.of(event(false, "", "", null)))
        );

        assertEquals(MvpErrorCode.AGENT_NO_FINAL_EVENT, exception.getErrorCode());
    }

    private StreamSnapshotEnvelope readSnapshotFixture(String fileName) throws Exception {
        return objectMapper.readValue(SNAPSHOT_FIXTURES.resolve(fileName).toFile(), StreamSnapshotEnvelope.class);
    }

    private List<GptProcessResult> readSseFixture(String fileName) throws Exception {
        List<GptProcessResult> events = new ArrayList<>();
        for (String line : Files.readAllLines(SSE_FIXTURES.resolve(fileName))) {
            if (!line.isBlank()) {
                events.add(objectMapper.readValue(line, GptProcessResult.class));
            }
        }
        return List.copyOf(events);
    }

    private GptProcessResult event(
            boolean finished,
            String responseAll,
            String response,
            Map<String, Object> resultMap
    ) {
        return GptProcessResult.builder()
                .finished(finished)
                .responseAll(responseAll)
                .response(response)
                .resultMap(resultMap)
                .packageType("result")
                .build();
    }
}
