package com.jd.genie.agent.dto;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileRequest {
    private String requestId;
    @JSONField(alternateNames = {"filename"})
    private String fileName;
    private String description;
    private String content;
}
