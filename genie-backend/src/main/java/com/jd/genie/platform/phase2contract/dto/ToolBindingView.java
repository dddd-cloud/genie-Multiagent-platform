package com.jd.genie.platform.phase2contract.dto;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ToolBindingView(
    List<String> directCapabilities,
    Map<String, List<String>> skillCapabilities,
    List<String> invalidCapabilities
) {
    public ToolBindingView {
        directCapabilities = directCapabilities == null
            ? List.of()
            : List.copyOf(directCapabilities);
        if (skillCapabilities == null) {
            skillCapabilities = Map.of();
        } else {
            Map<String, List<String>> copied = new LinkedHashMap<>();
            skillCapabilities.forEach((key, value) ->
                copied.put(key, value == null ? List.of() : List.copyOf(value))
            );
            skillCapabilities = Collections.unmodifiableMap(copied);
        }
        invalidCapabilities = invalidCapabilities == null
            ? List.of()
            : List.copyOf(invalidCapabilities);
    }
}
