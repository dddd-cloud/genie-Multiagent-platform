package com.jd.genie.agent.dto;

import com.alibaba.fastjson.JSON;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FileRequestJsonTest {
    @Test
    void acceptsToolSchemaFilenameAlias() {
        FileRequest parsed = JSON.parseObject(
                JSON.toJSONString(Map.of(
                        "command", "upload",
                        "filename", "品牌页.html",
                        "description", "品牌落地页",
                        "content", "<html></html>"
                )),
                FileRequest.class
        );
        assertEquals("品牌页.html", parsed.getFileName());
        assertEquals("品牌落地页", parsed.getDescription());
        assertEquals("<html></html>", parsed.getContent());
    }
}
