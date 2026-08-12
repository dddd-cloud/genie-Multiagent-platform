package com.jd.genie.platform.phase2contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.platform.phase2contract.dto.BrowserSkillExecutionManifest;
import com.jd.genie.platform.phase2contract.dto.BrowserSkillExecutionResult;
import com.jd.genie.platform.phase2contract.dto.BrowserSkillExecutionSignal;
import com.jd.genie.platform.phase2contract.dto.SkillEntrypointView;
import com.jd.genie.platform.phase2contract.dto.SkillExecutionResult;
import com.jd.genie.platform.phase2contract.enums.SkillEntrypointRuntime;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrowserSkillExecutionContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void constantsMatchFrozenStrings() {
        assertEquals(1, BrowserSkillExecutionContract.SCHEMA_VERSION);
        assertEquals("browser_skill_execution", BrowserSkillExecutionContract.PRINTER_MESSAGE_TYPE);
        assertEquals("skill_execution", BrowserSkillExecutionContract.SSE_PACKAGE_TYPE);
        assertEquals("browserSkillExecution", BrowserSkillExecutionContract.RESULT_MAP_KEY);
        assertEquals("__joyagent__/execution.json", BrowserSkillExecutionContract.EXECUTION_MANIFEST_PATH);
    }

    @Test
    void skillEntrypointRuntimeOrderIncludesPyodide() {
        assertArrayEquals(
            new String[]{"pyodide", "python", "node"},
            Arrays.stream(SkillEntrypointRuntime.values()).map(Enum::name).toArray(String[]::new)
        );
    }

    @Test
    void skillEntrypointViewPackagesDefaultAndCopy() {
        SkillEntrypointView legacy = new SkillEntrypointView(
            "run", SkillEntrypointRuntime.pyodide, "scripts/run.py", "d", "{}"
        );
        assertEquals(List.of(), legacy.packages());

        List<String> mutable = new java.util.ArrayList<>(List.of("numpy"));
        SkillEntrypointView view = new SkillEntrypointView(
            "run", SkillEntrypointRuntime.pyodide, "scripts/run.py", "d", "{}", mutable
        );
        mutable.add("pandas");
        assertEquals(List.of("numpy"), view.packages());
        assertThrows(UnsupportedOperationException.class, () -> view.packages().add("x"));
    }

    @Test
    void skillExecutionResultKeepsLegacyConstructor() {
        SkillExecutionResult result = new SkillExecutionResult(
            false, "", "", null, null, "legacy"
        );
        assertNull(result.outputJson());
        assertEquals("legacy", result.message());
    }

    @Test
    void browserSkillDtoRecordComponents() {
        assertRecordComponents(
            BrowserSkillExecutionSignal.class,
            "schemaVersion", "executionId", "skillId", "entrypointName", "packageHash", "timeoutMs"
        );
        assertRecordComponents(
            BrowserSkillExecutionManifest.class,
            "schemaVersion", "executionId", "entrypointName", "scriptRelativePath", "packages", "inputJson"
        );
        assertRecordComponents(
            BrowserSkillExecutionResult.class,
            "schemaVersion", "executionId", "success", "outputJson", "stdout", "stderr", "errorCode", "message"
        );
    }

    @Test
    void manifestPackagesAreImmutableCopy() throws Exception {
        List<String> mutable = new java.util.ArrayList<>(List.of("micropip"));
        BrowserSkillExecutionManifest manifest = new BrowserSkillExecutionManifest(
            1, "exec-1", "run", "scripts/run.py", mutable, "{}"
        );
        mutable.add("numpy");
        assertEquals(List.of("micropip"), manifest.packages());
        assertThrows(UnsupportedOperationException.class, () -> manifest.packages().add("x"));

        String json = objectMapper.writeValueAsString(manifest);
        assertTrue(json.contains("\"packages\""));
        assertTrue(json.contains("micropip"));
    }

    private static void assertRecordComponents(Class<?> type, String... expected) {
        String[] actual = Arrays.stream(type.getRecordComponents())
            .map(RecordComponent::getName)
            .toArray(String[]::new);
        assertArrayEquals(expected, actual);
    }
}
