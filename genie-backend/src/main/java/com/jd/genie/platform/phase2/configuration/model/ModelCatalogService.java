package com.jd.genie.platform.phase2.configuration.model;

import com.jd.genie.agent.llm.LLMSettings;
import com.jd.genie.config.GenieConfig;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2.configuration.agent.exception.AgentConfigurationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ModelCatalogService {
    public static final String SYSTEM_DEFAULT = "system-default";

    private final GenieConfig genieConfig;

    public List<ModelCatalogItem> listModels() {
        Map<String, LLMSettings> settings = safeSettings();
        String defaultModelName = genieConfig.getReactModelName();
        boolean defaultAvailable = defaultModelName != null && settings.containsKey(defaultModelName);
        List<ModelCatalogItem> items = settings.keySet().stream()
            .sorted(Comparator.naturalOrder())
            .map(key -> new ModelCatalogItem(key, key, false, true))
            .toList();
        return new java.util.ArrayList<>() {
            {
                add(new ModelCatalogItem(SYSTEM_DEFAULT, SYSTEM_DEFAULT, true, defaultAvailable));
                addAll(items);
            }
        };
    }

    public ModelResolutionResult resolveForStorage(String requestedModelName) {
        Map<String, LLMSettings> settings = safeSettings();
        String requested = requestedModelName == null ? "" : requestedModelName.trim();
        if (requested.isBlank() || SYSTEM_DEFAULT.equals(requested) || isLegacyDefaultAlias(requested, settings)) {
            String defaultModelName = genieConfig.getReactModelName();
            return new ModelResolutionResult(null, defaultModelName, defaultModelName != null && settings.containsKey(defaultModelName));
        }
        return new ModelResolutionResult(requested, requested, settings.containsKey(requested));
    }

    /** Catalog drafts historically stored "default"; that is not an llmSettingsMap key. */
    private static boolean isLegacyDefaultAlias(String requested, Map<String, LLMSettings> settings) {
        return "default".equals(requested) && !settings.containsKey(requested);
    }

    public ModelResolutionResult requireAvailableForStorage(String requestedModelName) {
        ModelResolutionResult result = resolveForStorage(requestedModelName);
        if (!result.available()) {
            throw new AgentConfigurationException(MvpErrorCode.MODEL_NOT_AVAILABLE, MvpErrorCode.MODEL_NOT_AVAILABLE.name());
        }
        return result;
    }

    private Map<String, LLMSettings> safeSettings() {
        Map<String, LLMSettings> settings = genieConfig.getLlmSettingsMap();
        return settings == null ? Map.of() : settings;
    }
}
