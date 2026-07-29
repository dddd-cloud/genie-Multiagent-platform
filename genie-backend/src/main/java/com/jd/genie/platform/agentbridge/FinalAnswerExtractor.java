package com.jd.genie.platform.agentbridge;

import com.jd.genie.model.response.GptProcessResult;
import com.jd.genie.platform.contract.MvpErrorCode;

import java.lang.reflect.Array;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class FinalAnswerExtractor {

    public String extract(List<GptProcessResult> events) {
        List<GptProcessResult> safeEvents = events == null ? List.of() : events;

        String responseAll = findLastFinishedText(safeEvents, true);
        if (responseAll != null) {
            return responseAll;
        }

        String response = findLastFinishedText(safeEvents, false);
        if (response != null) {
            return response;
        }

        String structuredAnswer = findStructuredAnswer(lastEvent(safeEvents));
        if (structuredAnswer != null) {
            return structuredAnswer;
        }

        throw new AgentBridgeException(
                MvpErrorCode.AGENT_NO_FINAL_EVENT,
                "Agent stream contains no extractable final answer"
        );
    }

    private String findLastFinishedText(List<GptProcessResult> events, boolean useResponseAll) {
        for (int index = events.size() - 1; index >= 0; index--) {
            GptProcessResult event = events.get(index);
            if (event == null || !event.isFinished()) {
                continue;
            }
            String value = useResponseAll ? event.getResponseAll() : event.getResponse();
            if (hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private GptProcessResult lastEvent(List<GptProcessResult> events) {
        return events.isEmpty() ? null : events.get(events.size() - 1);
    }

    private String findStructuredAnswer(GptProcessResult event) {
        if (event == null || event.getResultMap() == null) {
            return null;
        }
        Object eventData = event.getResultMap().get("eventData");
        String taskSummary = findTextByKey(eventData, "taskSummary");
        return taskSummary != null ? taskSummary : findTextByKey(eventData, "result");
    }

    private String findTextByKey(Object value, String targetKey) {
        if (value instanceof Map<?, ?> map) {
            String direct = asText(map.get(targetKey));
            if (direct != null) {
                return direct;
            }
            return map.entrySet().stream()
                    .filter(entry -> !targetKey.equals(String.valueOf(entry.getKey())))
                    .sorted(Comparator.comparing(entry -> String.valueOf(entry.getKey())))
                    .map(Map.Entry::getValue)
                    .map(child -> findTextByKey(child, targetKey))
                    .filter(this::hasText)
                    .findFirst()
                    .orElse(null);
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object child : iterable) {
                String found = findTextByKey(child, targetKey);
                if (found != null) {
                    return found;
                }
            }
        }
        if (value != null && value.getClass().isArray()) {
            for (int index = 0; index < Array.getLength(value); index++) {
                String found = findTextByKey(Array.get(value, index), targetKey);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private String asText(Object value) {
        return value instanceof CharSequence sequence && hasText(sequence.toString())
                ? sequence.toString()
                : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
