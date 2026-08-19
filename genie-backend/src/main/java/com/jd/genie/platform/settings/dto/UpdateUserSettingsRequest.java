package com.jd.genie.platform.settings.dto;

import java.util.Map;

public record UpdateUserSettingsRequest(Map<String, Object> settings) {
}
