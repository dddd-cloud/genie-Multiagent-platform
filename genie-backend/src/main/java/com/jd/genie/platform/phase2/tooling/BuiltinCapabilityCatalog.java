package com.jd.genie.platform.phase2.tooling;

import com.jd.genie.agent.agent.AgentContext;
import com.jd.genie.agent.tool.BaseTool;
import com.jd.genie.agent.tool.common.CodeInterpreterTool;
import com.jd.genie.agent.tool.common.DataAnalysisTool;
import com.jd.genie.agent.tool.common.DeepSearchTool;
import com.jd.genie.agent.tool.common.FileTool;
import com.jd.genie.agent.tool.common.ReportTool;
import com.jd.genie.platform.phase2contract.capability.CapabilityKeys;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public final class BuiltinCapabilityCatalog {
    private static final Map<String, Supplier<BaseTool>> FACTORIES = Map.of(
        CapabilityKeys.BUILTIN_CODE_INTERPRETER, CodeInterpreterTool::new,
        CapabilityKeys.BUILTIN_DATA_ANALYSIS, DataAnalysisTool::new,
        CapabilityKeys.BUILTIN_DEEP_SEARCH, DeepSearchTool::new,
        CapabilityKeys.BUILTIN_FILE, FileTool::new,
        CapabilityKeys.BUILTIN_REPORT, ReportTool::new
    );

    public Map<String, BaseTool> create(AgentContext context) {
        Map<String, BaseTool> result = new LinkedHashMap<>();
        for (Map.Entry<String, Supplier<BaseTool>> entry : FACTORIES.entrySet()) {
            BaseTool tool = entry.getValue().get();
            tool.getClass().getMethods();
            try { tool.getClass().getMethod("setAgentContext", AgentContext.class).invoke(tool, context); }
            catch (ReflectiveOperationException ignored) { }
            result.put(entry.getKey(), tool);
        }
        return result;
    }

    public boolean contains(String key) { return FACTORIES.containsKey(key); }
}
