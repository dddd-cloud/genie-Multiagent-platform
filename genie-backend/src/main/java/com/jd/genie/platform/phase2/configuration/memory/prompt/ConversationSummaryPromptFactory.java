package com.jd.genie.platform.phase2.configuration.memory.prompt;

import com.jd.genie.platform.phase2.configuration.memory.dto.ConversationSummaryAnalysisRequest;
import com.jd.genie.platform.phase2.configuration.memory.dto.ConversationSummaryTurn;
import org.springframework.stereotype.Component;

@Component
public class ConversationSummaryPromptFactory {
    public String systemPrompt() {
        return """
            You are a stateless conversation summary analyzer.
            Return only compact JSON with schemaVersion=1 and markdown.
            The markdown must contain exactly these four H2 sections in order:
            ## 当前目标
            ## 已确认事实
            ## 已完成内容
            ## 未解决事项
            Merge completed new turns into the existing summary. Do not include secrets, credentials, tokens, cookies, code fences, HTML, YAML front matter, hidden reasoning, or extra headings.

            Valid response (newlines inside markdown must be escaped as \\n):
            {"schemaVersion":1,"markdown":"## 当前目标\\n- 完成季度报告\\n\\n## 已确认事实\\n- 数据截至 2024Q3\\n\\n## 已完成内容\\n- 已产出数据表\\n\\n## 未解决事项\\n- 尚缺竞品对比\\n"}
            Invalid: markdown code fences around the JSON, prose outside the JSON, extra fields, more or fewer than the four H2 sections.
            """;
    }

    public String userPrompt(ConversationSummaryAnalysisRequest request) {
        StringBuilder builder = new StringBuilder();
        builder.append("conversationId: ").append(safe(request.conversationId())).append("\n\n");
        builder.append("currentSummary:\n").append(safe(request.currentSummary())).append("\n\n");
        builder.append("completedTurns:\n");
        for (ConversationSummaryTurn turn : request.newTurns()) {
            builder.append("- turnNo: ").append(turn.turnNo()).append('\n');
            builder.append("  userMessage: ").append(safe(turn.userMessage())).append('\n');
            builder.append("  assistantMessage: ").append(safe(turn.assistantMessage())).append("\n\n");
        }
        builder.append("Return JSON only.");
        return builder.toString();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
