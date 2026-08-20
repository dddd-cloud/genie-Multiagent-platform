package com.jd.genie.platform.phase2.configuration.model;

import com.jd.genie.agent.llm.LLMSettings;
import com.jd.genie.config.GenieConfig;
import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.CurrentUserProvider;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2.configuration.agent.exception.AgentConfigurationException;
import com.jd.genie.platform.phase2contract.error.Phase2ContractException;
import com.jd.genie.platform.settings.service.UserSettingService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ModelCatalogService {
    public static final String SYSTEM_DEFAULT = "system-default";

    private final GenieConfig genieConfig;
    private final UserLlmModelService userModels;
    private final CurrentUserProvider currentUserProvider;
    private final UserSettingService userSettingService;

    public ModelCatalogService(GenieConfig genieConfig) {
        this(genieConfig, null, null, null);
    }

    @Autowired
    public ModelCatalogService(
        GenieConfig genieConfig,
        ObjectProvider<UserLlmModelService> userModels,
        ObjectProvider<CurrentUserProvider> currentUserProvider,
        ObjectProvider<UserSettingService> userSettingService
    ) {
        this.genieConfig = genieConfig;
        this.userModels = userModels == null ? null : userModels.getIfAvailable();
        this.currentUserProvider = currentUserProvider == null ? null : currentUserProvider.getIfAvailable();
        this.userSettingService = userSettingService == null ? null : userSettingService.getIfAvailable();
    }

    public List<ModelCatalogItem> listModels() {
        CurrentUser user = currentUserOrNull();
        String preferred = preferredModelName(user);
        List<ModelCatalogItem> items = new ArrayList<>();
        if (user != null && userModels != null) {
            for (UserLlmModelRecord row : userModels.listAndSeed(user, safeSettings())) {
                items.add(toItem(row, false));
            }
        }
        if (items.isEmpty()) {
            Map<String, LLMSettings> settings = safeSettings();
            settings.keySet().stream()
                .sorted(Comparator.naturalOrder())
                .forEach(key -> items.add(toEnvItem(key, settings.get(key), false)));
        }
        markDefault(items, preferred);
        return List.copyOf(items);
    }

    public ModelCatalogItem getModel(String id) {
        CurrentUser user = requireUser();
        if (userModels != null) {
            try {
                UserLlmModelRecord row = userModels.get(user, id);
                return toItem(row, isPreferred(user, row.name()));
            } catch (Phase2ContractException ex) {
                if (ex.errorCode() != MvpErrorCode.RESOURCE_NOT_FOUND) {
                    throw ex;
                }
            }
        }
        Map<String, LLMSettings> settings = safeSettings();
        if (id != null && settings.containsKey(id.trim())) {
            String name = id.trim();
            return toEnvItem(name, settings.get(name), isPreferred(user, name));
        }
        throw new Phase2ContractException(MvpErrorCode.RESOURCE_NOT_FOUND, "Model not found");
    }

    public ModelCatalogItem createModel(LlmModelWriteRequest request) {
        CurrentUser user = requireUser();
        if (userModels == null) {
            throw new Phase2ContractException(MvpErrorCode.INTERNAL_ERROR, "Model store is not available");
        }
        UserLlmModelRecord created = userModels.create(user, request);
        return toItem(created, isPreferred(user, created.name()));
    }

    public ModelCatalogItem updateModel(String id, LlmModelWriteRequest request) {
        CurrentUser user = requireUser();
        if (userModels == null) {
            throw new Phase2ContractException(MvpErrorCode.INTERNAL_ERROR, "Model store is not available");
        }
        UserLlmModelRecord updated = userModels.update(user, id, request);
        return toItem(updated, isPreferred(user, updated.name()));
    }

    public void deleteModel(String id) {
        CurrentUser user = requireUser();
        if (userModels == null) {
            throw new Phase2ContractException(MvpErrorCode.INTERNAL_ERROR, "Model store is not available");
        }
        userModels.delete(user, id);
    }

    public ModelResolutionResult resolveForStorage(String requestedModelName) {
        Map<String, LLMSettings> settings = safeSettings();
        String requested = requestedModelName == null ? "" : requestedModelName.trim();
        if (requested.isBlank() || SYSTEM_DEFAULT.equals(requested) || isLegacyDefaultAlias(requested, settings)) {
            String defaultModelName = genieConfig.getReactModelName();
            return new ModelResolutionResult(
                null,
                defaultModelName,
                defaultModelName != null && settings.containsKey(defaultModelName)
            );
        }
        boolean inEnv = settings.containsKey(requested);
        boolean inUser = userModels != null && currentUserOrNull() != null
            && userModels.findByName(currentUserOrNull().tenantId(), currentUserOrNull().userId(), requested) != null;
        return new ModelResolutionResult(requested, requested, inEnv || inUser);
    }

    public ModelResolutionResult requireAvailableForStorage(String requestedModelName) {
        ModelResolutionResult result = resolveForStorage(requestedModelName);
        if (!result.available()) {
            throw new AgentConfigurationException(MvpErrorCode.MODEL_NOT_AVAILABLE, MvpErrorCode.MODEL_NOT_AVAILABLE.name());
        }
        return result;
    }

    /**
     * Resolve the single model used by every agent for this request.
     * Requested name wins, then the user's preferred model, then the first configured catalog entry.
     */
    public LLMSettings resolveRuntimeSettings(String tenantId, String ownerId, String requestedModelName) {
        ResolvedLlm resolved = resolveRuntime(tenantId, ownerId, requestedModelName);
        if (resolved == null || resolved.settings() == null) {
            throw new Phase2ContractException(MvpErrorCode.MODEL_NOT_AVAILABLE, MvpErrorCode.MODEL_NOT_AVAILABLE.name());
        }
        return resolved.settings();
    }

    public String resolveRuntimeName(String tenantId, String ownerId, String requestedModelName) {
        ResolvedLlm resolved = resolveRuntime(tenantId, ownerId, requestedModelName);
        return resolved == null ? null : resolved.name();
    }

    private ResolvedLlm resolveRuntime(String tenantId, String ownerId, String requestedModelName) {
        String requested = requestedModelName == null ? "" : requestedModelName.trim();
        if (SYSTEM_DEFAULT.equals(requested) || "default".equals(requested)) {
            requested = "";
        }
        if (!requested.isBlank()) {
            ResolvedLlm named = lookupNamed(tenantId, ownerId, requested);
            if (named != null) {
                return named;
            }
            throw new Phase2ContractException(MvpErrorCode.MODEL_NOT_AVAILABLE, MvpErrorCode.MODEL_NOT_AVAILABLE.name());
        }
        String preferred = preferredModelName(tenantId, ownerId);
        if (!preferred.isBlank()) {
            ResolvedLlm named = lookupNamed(tenantId, ownerId, preferred);
            if (named != null) {
                return named;
            }
        }
        if (userModels != null) {
            List<UserLlmModelRecord> owned = userModels.listByOwner(tenantId, ownerId);
            for (UserLlmModelRecord row : owned) {
                if (row.apiKeyConfigured() && row.model() != null && !row.model().isBlank()) {
                    return new ResolvedLlm(row.name(), userModels.toSettings(row));
                }
            }
        }
        Map<String, LLMSettings> settings = safeSettings();
        String react = genieConfig.getReactModelName();
        if (react != null && settings.containsKey(react)) {
            return new ResolvedLlm(react, copySettings(settings.get(react)));
        }
        return settings.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> new ResolvedLlm(entry.getKey(), copySettings(entry.getValue())))
            .findFirst()
            .orElse(null);
    }

    private ResolvedLlm lookupNamed(String tenantId, String ownerId, String name) {
        if (userModels != null) {
            UserLlmModelRecord row = userModels.findByName(tenantId, ownerId, name);
            if (row != null) {
                return new ResolvedLlm(row.name(), userModels.toSettings(row));
            }
        }
        Map<String, LLMSettings> settings = safeSettings();
        if (settings.containsKey(name)) {
            return new ResolvedLlm(name, copySettings(settings.get(name)));
        }
        return null;
    }

    private static LLMSettings copySettings(LLMSettings source) {
        if (source == null) {
            return LLMSettings.builder().build();
        }
        return LLMSettings.builder()
            .model(source.getModel())
            .maxTokens(source.getMaxTokens())
            .temperature(source.getTemperature())
            .apiType(source.getApiType())
            .apiKey(source.getApiKey())
            .apiVersion(source.getApiVersion())
            .baseUrl(source.getBaseUrl())
            .interfaceUrl(source.getInterfaceUrl())
            .functionCallType(source.getFunctionCallType())
            .maxInputTokens(source.getMaxInputTokens())
            .extParams(source.getExtParams())
            .build();
    }

    private ModelCatalogItem toItem(UserLlmModelRecord row, boolean isDefault) {
        boolean configured = row.apiKeyConfigured();
        boolean available = configured && row.model() != null && !row.model().isBlank();
        return new ModelCatalogItem(
            row.id(),
            row.name(),
            row.displayName(),
            row.model(),
            row.baseUrl(),
            row.interfaceUrl(),
            row.maxTokens(),
            row.temperature(),
            row.maxInputTokens(),
            configured,
            configured ? ModelCatalogItem.MASKED_API_KEY : null,
            isDefault,
            available,
            "user"
        );
    }

    private ModelCatalogItem toEnvItem(String name, LLMSettings settings, boolean isDefault) {
        LLMSettings safe = settings == null ? LLMSettings.builder().build() : settings;
        boolean configured = safe.getApiKey() != null && !safe.getApiKey().isBlank();
        boolean available = configured && safe.getModel() != null && !safe.getModel().isBlank();
        return new ModelCatalogItem(
            name,
            name,
            name,
            safe.getModel(),
            safe.getBaseUrl(),
            safe.getInterfaceUrl(),
            safe.getMaxTokens() <= 0 ? null : safe.getMaxTokens(),
            safe.getTemperature(),
            safe.getMaxInputTokens() <= 0 ? null : safe.getMaxInputTokens(),
            configured,
            configured ? ModelCatalogItem.MASKED_API_KEY : null,
            isDefault,
            available,
            "env"
        );
    }

    private void markDefault(List<ModelCatalogItem> items, String preferred) {
        if (items.isEmpty()) {
            return;
        }
        int index = -1;
        if (!preferred.isBlank()) {
            for (int i = 0; i < items.size(); i++) {
                if (preferred.equals(items.get(i).name())) {
                    index = i;
                    break;
                }
            }
        }
        if (index < 0) {
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i).available()) {
                    index = i;
                    break;
                }
            }
        }
        if (index < 0) {
            index = 0;
        }
        ModelCatalogItem chosen = items.get(index);
        items.set(index, withDefault(chosen, true));
        for (int i = 0; i < items.size(); i++) {
            if (i != index && items.get(i).isDefault()) {
                items.set(i, withDefault(items.get(i), false));
            }
        }
    }

    private static ModelCatalogItem withDefault(ModelCatalogItem item, boolean isDefault) {
        return new ModelCatalogItem(
            item.id(),
            item.name(),
            item.displayName(),
            item.model(),
            item.baseUrl(),
            item.interfaceUrl(),
            item.maxTokens(),
            item.temperature(),
            item.maxInputTokens(),
            item.apiKeyConfigured(),
            item.apiKeyMasked(),
            isDefault,
            item.available(),
            item.source()
        );
    }

    private static boolean isLegacyDefaultAlias(String requested, Map<String, LLMSettings> settings) {
        return "default".equals(requested) && !settings.containsKey(requested);
    }

    private Map<String, LLMSettings> safeSettings() {
        Map<String, LLMSettings> settings = genieConfig.getLlmSettingsMap();
        return settings == null ? Map.of() : new LinkedHashMap<>(settings);
    }

    private CurrentUser currentUserOrNull() {
        if (currentUserProvider == null) {
            return null;
        }
        try {
            return currentUserProvider.requireCurrentUser();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private CurrentUser requireUser() {
        CurrentUser user = currentUserOrNull();
        if (user == null) {
            throw new Phase2ContractException(MvpErrorCode.AUTH_REQUIRED, "Authentication required");
        }
        return user;
    }

    private boolean isPreferred(CurrentUser user, String name) {
        return name != null && name.equals(preferredModelName(user));
    }

    private String preferredModelName(CurrentUser user) {
        if (user == null) {
            return "";
        }
        return preferredModelName(user.tenantId(), user.userId());
    }

    private String preferredModelName(String tenantId, String ownerId) {
        if (userSettingService == null || tenantId == null || ownerId == null) {
            return "";
        }
        try {
            Object value = userSettingService.get(
                    new CurrentUser(tenantId, ownerId, ownerId, ownerId, com.jd.genie.platform.contract.UserRole.USER)
                )
                .settings()
                .get("preferredModelName");
            return value == null ? "" : String.valueOf(value).trim();
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private record ResolvedLlm(String name, LLMSettings settings) {
    }
}
