package com.jd.genie.platform.phase2contract.dto;

public record SkillResource(
    String skillId,
    String relativePath,
    String contentType,
    byte[] content
) {
    public SkillResource {
        content = content == null ? new byte[0] : content.clone();
    }

    public byte[] content() {
        return content.clone();
    }
}
