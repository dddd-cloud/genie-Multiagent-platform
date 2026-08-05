package com.jd.genie.platform.phase2.configuration.memory;

import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2.configuration.memory.exception.MemoryAnalysisException;
import com.jd.genie.platform.phase2.configuration.memory.validation.ConversationSummaryValidator;
import com.jd.genie.platform.phase2.configuration.memory.validation.MemorySecretFilter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversationSummaryValidatorTest {
    private final ConversationSummaryValidator validator = new ConversationSummaryValidator(new MemorySecretFilter());

    @Test
    void acceptsExactlyFourFixedSectionsInOrder() {
        var response = validator.parseAndValidate(validJson());

        assertEquals(1, response.schemaVersion());
        assertTrue(response.markdown().contains("## 当前目标"));
        assertTrue(response.markdown().contains("## 未解决事项"));
    }

    @Test
    void rejectsExtraMissingOrReorderedSections() {
        assertSummaryFailed("""
            {"schemaVersion":1,"markdown":"## 当前目标\\n- A\\n\\n## 已完成内容\\n- B\\n\\n## 已确认事实\\n- C\\n\\n## 未解决事项\\n- D"}
            """);
        assertSummaryFailed("""
            {"schemaVersion":1,"markdown":"## 当前目标\\n- A\\n\\n## 已确认事实\\n- B\\n\\n## 已完成内容\\n- C"}
            """);
        assertSummaryFailed("""
            {"schemaVersion":1,"markdown":"## 当前目标\\n- A\\n\\n## 已确认事实\\n- B\\n\\n## 已完成内容\\n- C\\n\\n## 未解决事项\\n- D\\n\\n## 额外\\n- E"}
            """);
    }

    @Test
    void rejectsSecretsCodeFencesAndUnknownJsonFields() {
        assertSummaryFailed("""
            {"schemaVersion":1,"markdown":"## 当前目标\\n- token=abcdef123456\\n\\n## 已确认事实\\n- B\\n\\n## 已完成内容\\n- C\\n\\n## 未解决事项\\n- D"}
            """);
        assertSummaryFailed("""
            {"schemaVersion":1,"markdown":"## 当前目标\\n```json\\n{}\\n```\\n\\n## 已确认事实\\n- B\\n\\n## 已完成内容\\n- C\\n\\n## 未解决事项\\n- D"}
            """);
        assertSummaryFailed("""
            {"schemaVersion":1,"markdown":"## 当前目标\\n- A\\n\\n## 已确认事实\\n- B\\n\\n## 已完成内容\\n- C\\n\\n## 未解决事项\\n- D","extra":true}
            """);
    }

    static String validJson() {
        return """
            {"schemaVersion":1,"markdown":"# 当前对话摘要\\n\\n## 当前目标\\n- 制定 Docker 学习计划\\n\\n## 已确认事实\\n- 学生没有 Docker 基础\\n\\n## 已完成内容\\n- 已确认三天节奏\\n\\n## 未解决事项\\n- 待实践验证"}
            """;
    }

    private void assertSummaryFailed(String json) {
        MemoryAnalysisException ex = assertThrows(MemoryAnalysisException.class, () -> validator.parseAndValidate(json));
        assertEquals(MvpErrorCode.SUMMARY_FAILED, ex.code());
    }
}
