package com.jd.genie.platform.phase2.skillruntime.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jd.genie.agent.agent.AgentContext;
import com.jd.genie.agent.tool.BaseTool;
import com.jd.genie.agent.tool.ToolCollection;
import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2.skillruntime.packageinfo.SkillPackageLimits;
import com.jd.genie.platform.phase2contract.BrowserSkillExecutionContract;
import com.jd.genie.platform.phase2contract.dto.BrowserSkillExecutionResult;
import com.jd.genie.platform.phase2contract.dto.BrowserSkillExecutionSignal;
import com.jd.genie.platform.phase2contract.dto.SkillEntrypointView;
import com.jd.genie.platform.phase2contract.error.Phase2ContractException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class BrowserPyodideSkillTool implements BaseTool {
    private final String name, skillId;
    private final CurrentUser user;
    private final AgentContext context;
    private final SkillPackageBytesSnapshot snapshot;
    private final SkillEntrypointView entrypoint;
    private final BrowserSkillExecutionCoordinator coordinator;
    private final ObjectMapper mapper;
    private final long timeoutMs;

    public BrowserPyodideSkillTool(String name, String skillId, CurrentUser user, AgentContext context,
                                   SkillPackageBytesSnapshot snapshot, SkillEntrypointView entrypoint,
                                   BrowserSkillExecutionCoordinator coordinator, ObjectMapper mapper, long timeoutMs) {
        this.name=name; this.skillId=skillId; this.user=user; this.context=context; this.snapshot=snapshot;
        this.entrypoint=entrypoint; this.coordinator=coordinator; this.mapper=mapper; this.timeoutMs=timeoutMs;
    }
    @Override public String getName() { return name; }
    @Override public String getDescription() { return entrypoint.description() == null ? "Run browser Skill entrypoint" : entrypoint.description(); }
    @Override public Map<String,Object> toParams() {
        if (entrypoint.inputSchemaJson() != null && !entrypoint.inputSchemaJson().isBlank()) {
            try { return mapper.convertValue(mapper.readTree(entrypoint.inputSchemaJson()), Map.class); }
            catch (Exception e) { throw new Phase2ContractException(MvpErrorCode.SKILL_PACKAGE_INVALID, "invalid entrypoint input schema"); }
        }
        Map<String,Object> p=new LinkedHashMap<>(); p.put("type","object"); p.put("additionalProperties",true); return p;
    }
    @Override public Object execute(Object input) {
        if (context == null || context.getPrinter() == null)
            throw new Phase2ContractException(MvpErrorCode.TOOL_NOT_BOUND, "compatible printer unavailable");
        String inputJson = inputJson(input);
        var execution = coordinator.register(user, skillId, snapshot, entrypoint, inputJson, timeoutMs);
        try {
            var signal = new BrowserSkillExecutionSignal(1, execution.executionId(), skillId, entrypoint.name(),
                snapshot.packageHash(), timeoutMs);
            try {
                context.getPrinter().send(null, BrowserSkillExecutionContract.PRINTER_MESSAGE_TYPE, signal, null, false);
            } catch (RuntimeException e) {
                coordinator.cancel(execution.executionId());
                throw new Phase2ContractException(MvpErrorCode.TOOL_NOT_BOUND, "compatible printer unavailable", e);
            }
            BrowserSkillExecutionResult result = execution.future().get(timeoutMs, TimeUnit.MILLISECONDS);
            if (!result.success()) throw new Phase2ContractException(MvpErrorCode.SKILL_EXECUTION_FAILED,
                result.message() == null ? "browser skill execution failed" : result.message());
            validateJson(result.outputJson());
            return visibleToAgent(result.outputJson());
        } catch (TimeoutException e) {
            coordinator.expire(execution.executionId());
            throw new Phase2ContractException(MvpErrorCode.TOOL_TIMEOUT, "browser skill execution timed out", e);
        } catch (InterruptedException e) {
            coordinator.cancel(execution.executionId()); Thread.currentThread().interrupt();
            throw new Phase2ContractException(MvpErrorCode.CLIENT_DISCONNECTED, "browser skill execution interrupted", e);
        } catch (ExecutionException e) {
            throw new Phase2ContractException(MvpErrorCode.SKILL_EXECUTION_FAILED, "browser skill execution failed", e);
        } finally {
            coordinator.release(execution.executionId());
        }
    }
    private String inputJson(Object input) {
        try {
            String value = input instanceof String s ? s : mapper.writeValueAsString(input == null ? Map.of() : input);
            try { JsonNode ignored = mapper.readTree(value); if (ignored == null) throw new IllegalArgumentException(); }
            catch (Exception e) { throw new Phase2ContractException(MvpErrorCode.TOOL_INVALID_INPUT, "tool input invalid", e); }
            if (value.getBytes(StandardCharsets.UTF_8).length > SkillPackageLimits.MAX_INPUT_JSON_BYTES)
                throw new Phase2ContractException(MvpErrorCode.TOOL_INVALID_INPUT, "tool input too large");
            return value;
        } catch (Phase2ContractException e) { throw e; }
        catch (Exception e) { throw new Phase2ContractException(MvpErrorCode.TOOL_INVALID_INPUT, "tool input invalid", e); }
    }
    private void validateJson(String value) {
        try { JsonNode ignored = mapper.readTree(value); if (ignored == null) throw new IllegalArgumentException(); }
        catch (Exception e) { throw new Phase2ContractException(MvpErrorCode.TOOL_INVALID_RESPONSE, "invalid JSON", e); }
    }

    /**
     * Configured agents only observe 2000 chars, then cannot call file_tool.
     * Upload generated HTML ourselves and keep the model-visible payload tiny
     * so the SUCCESS envelope stays valid JSON.
     */
    private String visibleToAgent(String outputJson) {
        try {
            JsonNode root = mapper.readTree(outputJson);
            if (root == null || !root.isObject()) {
                return outputJson;
            }
            JsonNode htmlNode = root.get("html");
            JsonNode filenameNode = root.get("filename");
            if (htmlNode == null || !htmlNode.isTextual() || htmlNode.asText().isBlank()
                    || filenameNode == null || !filenameNode.isTextual() || filenameNode.asText().isBlank()) {
                return outputJson;
            }
            String filename = filenameNode.asText();
            boolean uploaded = uploadGeneratedHtml(filename, htmlNode.asText());
            ObjectNode copy = root.deepCopy();
            copy.remove("html");
            copy.put("htmlOmitted", true);
            copy.put("previewFile", filename);
            copy.put("uploaded", uploaded);
            return mapper.writeValueAsString(copy);
        } catch (Exception ignored) {
            return outputJson;
        }
    }

    private boolean uploadGeneratedHtml(String filename, String html) {
        ToolCollection tools = context.getToolCollection();
        if (tools == null || tools.getTool("file_tool") == null) {
            return false;
        }
        try {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("command", "upload");
            params.put("filename", filename);
            params.put("description", "生成艺术预览");
            params.put("content", html);
            Object result = tools.execute("file_tool", params);
            return result != null;
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
