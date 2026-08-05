package com.jd.genie.platform.phase2.configuration.memory.prompt;

import com.jd.genie.platform.phase2.configuration.memory.dto.MemoryAnalysisRequest;
import org.springframework.stereotype.Component;

@Component
public class MemoryAnalysisPromptFactory {
    public String systemPrompt() {
        return """
            You are a stateless long-term memory patch analyzer.
            Return only compact JSON matching schemaVersion=1 and patches=[].
            Allowed operations are UPSERT and DELETE.
            Allowed sections are: 基本信息, 回答偏好, 长期目标, 长期约束.
            Create patches only for durable user facts, preferences, goals, or constraints explicitly stated by the user.
            Do not infer memory from assistant suggestions. Do not include secrets, credentials, tokens, cookies, IDs, file paths, markdown headings, code fences, HTML, YAML front matter, or unknown schema fields.
            For DELETE, value must be null. For UPSERT, value must be a concise plain text string.
            """;
    }

    public String userPrompt(MemoryAnalysisRequest request) {
        return """
            conversationId: %s
            turnStatus: %s

            currentLongTermMemory:
            %s

            userMessage:
            %s

            assistantMessage:
            %s

            Return JSON only.
            """.formatted(
            safe(request.conversationId()),
            safe(request.turnStatus()),
            safe(request.currentLongTermMemory()),
            safe(request.userMessage()),
            safe(request.assistantMessage())
        );
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
