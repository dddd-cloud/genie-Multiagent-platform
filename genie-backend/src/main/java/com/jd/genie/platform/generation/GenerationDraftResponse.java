package com.jd.genie.platform.generation;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public record GenerationDraftResponse(
    GenerationTarget target, String name, String summary, double confidence,
    JsonNode draft, List<String> matchedResourceIds, List<String> recommendedMarketplaceResources,
    List<String> suggestions,
    String status, List<String> missingFields, List<String> matchReasons
) {}
