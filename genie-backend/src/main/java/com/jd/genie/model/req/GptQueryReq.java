package com.jd.genie.model.req;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GptQueryReq {
    private String query;
    private String sessionId;
    private String requestId;
    private Integer deepThink;
    /**
     * 前端传入交付物格式：html(网页模式）,docs(文档模式）， table(表格模式）
     */
    private String outputStyle;
    private String traceId;
    private String user;
    @JsonIgnore
    private List<AgentRequest.Message> historyMessages;
    @JsonIgnore
    private String runtimeBasePrompt;
    @JsonIgnore
    private String runtimeSopPrompt;
}
