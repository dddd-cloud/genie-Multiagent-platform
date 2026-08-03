package com.jd.genie.agent.tool.mcp;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpToolContractTest {

    @Test
    void keepsLegacyToolNameAndRequestJsonShape() {
        McpTool tool = new McpTool();

        assertEquals("mcp_tool", tool.getName());

        McpTool.McpToolRequest request =
                McpTool.McpToolRequest.builder()
                        .server_url("https://mcp.example.test/sse")
                        .name("get_current_time")
                        .arguments(Map.of("timezone", "America/New_York"))
                        .build();

        JSONObject json =
                JSON.parseObject(JSON.toJSONString(request));

        assertEquals(
                "https://mcp.example.test/sse",
                json.getString("server_url")
        );
        assertEquals(
                "get_current_time",
                json.getString("name")
        );
        assertTrue(json.getJSONObject("arguments")
                .containsKey("timezone"));
    }
}
