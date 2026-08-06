package com.jd.genie.platform.phase2.tooling;

import com.jd.genie.agent.tool.BaseTool;
import com.jd.genie.agent.tool.ToolCollection;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class AuthorizedToolCollection extends ToolCollection {
    private final Map<String, BaseTool> authorized = new LinkedHashMap<>();

    public AuthorizedToolCollection(Collection<? extends BaseTool> tools) {
        super();
        if (tools != null) for (BaseTool tool : tools) {
            if (tool == null || tool.getName() == null || tool.getName().isBlank()) throw new ToolCapabilityException("invalid tool");
            if (authorized.putIfAbsent(tool.getName(), tool) != null) throw new ToolCapabilityException("tool runtime name conflict");
            addTool(tool);
        }
    }

    public Map<String, BaseTool> authorizedTools() { return Map.copyOf(authorized); }

    @Override
    public Object execute(String name, Object input) {
        BaseTool tool = authorized.get(name);
        if (tool == null) throw new ToolCapabilityException("tool is not bound");
        return tool.execute(input);
    }
}
