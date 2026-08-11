package com.jd.genie.platform.phase2contract.dto;

/**
 * Shared version body for configuration mutation endpoints.
 * Moved out of shared configuration.api so Agent/Skill/Memory no longer share a business file.
 */
public record VersionRequest(Long version) {
}
