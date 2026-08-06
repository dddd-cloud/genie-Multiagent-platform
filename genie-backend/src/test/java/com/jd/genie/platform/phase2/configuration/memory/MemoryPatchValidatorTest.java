package com.jd.genie.platform.phase2.configuration.memory;

import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2.configuration.memory.exception.MemoryAnalysisException;
import com.jd.genie.platform.phase2.configuration.memory.validation.MemoryMarkdownGuard;
import com.jd.genie.platform.phase2.configuration.memory.validation.MemoryPatchValidator;
import com.jd.genie.platform.phase2.configuration.memory.validation.MemorySecretFilter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MemoryPatchValidatorTest {
    private final MemoryPatchValidator validator = new MemoryPatchValidator(new MemorySecretFilter(), new MemoryMarkdownGuard());

    @Test
    void acceptsUpsertDeleteAndEmptyPatchResponses() {
        var upsert = validator.parseAndValidate("""
            {"schemaVersion":1,"patches":[{"operation":"UPSERT","section":"基本信息","key":"city","value":"用户常驻杭州"}]}
            """);
        var delete = validator.parseAndValidate("""
            {"schemaVersion":1,"patches":[{"operation":"DELETE","section":"回答偏好","key":"format","value":null}]}
            """);
        var empty = validator.parseAndValidate("{\"schemaVersion\":1,\"patches\":[]}");

        assertEquals("UPSERT", upsert.patches().get(0).operation());
        assertEquals("用户常驻杭州", upsert.patches().get(0).value());
        assertEquals("DELETE", delete.patches().get(0).operation());
        assertNull(delete.patches().get(0).value());
        assertEquals(0, empty.patches().size());
    }

    @Test
    void rejectsUnknownFieldsInvalidSchemaAndDuplicateKeys() {
        assertMemoryFailed("{\"schemaVersion\":2,\"patches\":[]}");
        assertMemoryFailed("{\"schemaVersion\":1,\"patches\":[],\"extra\":true}");
        assertMemoryFailed("""
            {"schemaVersion":1,"patches":[
              {"operation":"UPSERT","section":"基本信息","key":"city","value":"杭州"},
              {"operation":"DELETE","section":"基本信息","key":"city","value":null}
            ]}
            """);
    }

    @Test
    void rejectsSecretsAndMarkdownInjectionInPatchValues() {
        assertMemoryFailed("""
            {"schemaVersion":1,"patches":[{"operation":"UPSERT","section":"长期约束","key":"credential","value":"token=abcdef123456"}]}
            """);
        assertMemoryFailed("""
            {"schemaVersion":1,"patches":[{"operation":"UPSERT","section":"长期目标","key":"goal","value":"```json\\n{}\\n```"}]}
            """);
        assertMemoryFailed("""
            {"schemaVersion":1,"patches":[{"operation":"UPSERT","section":"回答偏好","key":"link","value":"[x](javascript:alert(1))"}]}
            """);
    }

    @Test
    void rejectsPartialJsonCodeFenceAndInvalidValueShape() {
        assertMemoryFailed("```json\n{\"schemaVersion\":1,\"patches\":[]}\n```");
        assertMemoryFailed("[{\"schemaVersion\":1}]");
        assertMemoryFailed("""
            {"schemaVersion":1,"patches":[{"operation":"UPSERT","section":"回答偏好","key":"format","value":""}]}
            """);
        assertMemoryFailed("""
            {"schemaVersion":1,"patches":[{"operation":"DELETE","section":"回答偏好","key":"format","value":"remove"}]}
            """);
    }

    private void assertMemoryFailed(String json) {
        MemoryAnalysisException ex = assertThrows(MemoryAnalysisException.class, () -> validator.parseAndValidate(json));
        assertEquals(MvpErrorCode.MEMORY_ANALYSIS_FAILED, ex.code());
    }
}
