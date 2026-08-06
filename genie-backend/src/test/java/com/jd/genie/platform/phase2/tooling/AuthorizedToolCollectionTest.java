package com.jd.genie.platform.phase2.tooling;

import com.jd.genie.agent.tool.BaseTool;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

class AuthorizedToolCollectionTest {
    private static BaseTool tool(String name) { return new BaseTool() { public String getName(){return name;} public String getDescription(){return "";} public Map<String,Object> toParams(){return Map.of();} public Object execute(Object input){return "ok";} }; }
    @Test void emptyCollectionIsUsableAndUnknownIsRejected() { var c = new AuthorizedToolCollection(List.of()); assertThat(c.getToolMap()).isEmpty(); assertThatThrownBy(() -> c.execute("x", Map.of())).isInstanceOf(ToolCapabilityException.class); }
    @Test void duplicateRuntimeNamesFailDeterministically() { assertThatThrownBy(() -> new AuthorizedToolCollection(List.of(tool("same"), tool("same")))).isInstanceOf(ToolCapabilityException.class); }
}
