package com.jd.genie.agent.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.agent.dto.tool.ToolCall;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BaseAgentToolArgumentsTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void preservesValidObjectArguments() throws Exception {
        String normalized = BaseAgent.normalizeToolArgumentsJson(
                mapper,
                "{\"command\":\"run_code\",\"code\":\"print(1)\"}"
        );

        assertThat(asMap(normalized))
                .containsEntry("command", "run_code")
                .containsEntry("code", "print(1)");
    }

    @Test
    void repairsOneDuplicatedOpeningBrace() throws Exception {
        String normalized = BaseAgent.normalizeToolArgumentsJson(
                mapper,
                "{{\"command\":\"run_code\",\"code\":\"print(1)\"}"
        );

        assertThat(asMap(normalized))
                .containsEntry("command", "run_code")
                .containsEntry("code", "print(1)");
    }

    @Test
    void rejectsMalformedOrNonObjectArguments() {
        assertThatThrownBy(() -> BaseAgent.normalizeToolArgumentsJson(
                mapper,
                "{{{\"command\":\"run_code\"}"
        )).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> BaseAgent.normalizeToolArgumentsJson(mapper, "[1,2,3]"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JSON object");
    }

    @Test
    void assignsUniqueIdsWhenProviderOmitsOrDuplicatesThem() {
        ToolCall first = ToolCall.builder().id("").build();
        ToolCall second = ToolCall.builder().id("").build();
        ToolCall third = ToolCall.builder().id("provider-id").build();
        ToolCall fourth = ToolCall.builder().id("provider-id").build();

        BaseAgent.ensureUniqueToolCallIds(List.of(first, second, third, fourth));

        assertThat(List.of(first.getId(), second.getId(), third.getId(), fourth.getId()))
                .doesNotContainNull()
                .doesNotHaveDuplicates();
        assertThat(first.getId()).isNotBlank();
        assertThat(second.getId()).isNotBlank();
        assertThat(third.getId()).isEqualTo("provider-id");
        assertThat(fourth.getId()).isNotEqualTo("provider-id");
    }

    private Map<String, Object> asMap(String json) throws Exception {
        return mapper.readValue(json, new TypeReference<>() { });
    }
}
