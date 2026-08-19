package com.jd.genie.platform.settings.service;

import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.settings.dto.UpdateUserSettingsRequest;
import com.jd.genie.platform.settings.dto.UserSettingsResponse;
import com.jd.genie.platform.settings.entity.UserSettingEntity;
import com.jd.genie.platform.settings.mapper.UserSettingMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Server-authoritative user preferences. The response always contains every whitelisted key so the
 * client never has to know defaults; stored rows only exist for keys the user explicitly changed.
 */
@Service
public class UserSettingService {

    private static final int MAX_STRING_LENGTH = 128;

    private sealed interface SettingSpec {
        Object defaultValue();

        String toStorage(Object raw);

        Object fromStorage(String stored);
    }

    private record BooleanSpec(Boolean defaultValue) implements SettingSpec {
        @Override
        public String toStorage(Object raw) {
            if (raw instanceof Boolean value) {
                return value ? "true" : "false";
            }
            throw new UserSettingValidationException("expected a boolean value");
        }

        @Override
        public Object fromStorage(String stored) {
            return "true".equals(stored);
        }
    }

    private record EnumSpec(String defaultValue, Set<String> allowed) implements SettingSpec {
        @Override
        public String toStorage(Object raw) {
            if (raw instanceof String value && allowed.contains(value)) {
                return value;
            }
            throw new UserSettingValidationException("expected one of " + allowed);
        }

        @Override
        public Object fromStorage(String stored) {
            return allowed.contains(stored) ? stored : defaultValue;
        }
    }

    private record StringSpec(String defaultValue) implements SettingSpec {
        @Override
        public String toStorage(Object raw) {
            if (raw instanceof String value && value.length() <= MAX_STRING_LENGTH) {
                return value;
            }
            throw new UserSettingValidationException("expected a string of at most " + MAX_STRING_LENGTH + " characters");
        }

        @Override
        public Object fromStorage(String stored) {
            return stored;
        }
    }

    /**
     * The whitelist is the contract: an unknown key is rejected rather than silently stored, so a
     * client typo can never become a persisted preference nobody can find again.
     */
    private static final Map<String, SettingSpec> SPECS = Map.of(
        "defaultExecutionMode", new EnumSpec("AUTO", Set.of("AUTO", "DIRECT", "ORCHESTRATED")),
        "defaultDeepThink", new BooleanSpec(Boolean.FALSE),
        "defaultOutputStyle", new StringSpec(""),
        "preferredModelName", new StringSpec(""),
        "streamRenderMode", new EnumSpec("BATCHED", Set.of("BATCHED", "INSTANT")),
        "sidebarCollapsed", new BooleanSpec(Boolean.FALSE),
        "locale", new EnumSpec("zh-CN", Set.of("zh-CN", "en-US"))
    );

    private final UserSettingMapper userSettingMapper;
    private final Clock clock;

    public UserSettingService(UserSettingMapper userSettingMapper, Clock clock) {
        this.userSettingMapper = userSettingMapper;
        this.clock = clock;
    }

    public UserSettingsResponse get(CurrentUser currentUser) {
        return new UserSettingsResponse(resolve(currentUser));
    }

    @Transactional
    public UserSettingsResponse update(CurrentUser currentUser, UpdateUserSettingsRequest request) {
        if (request == null || request.settings() == null || request.settings().isEmpty()) {
            throw new UserSettingValidationException("settings is required");
        }
        if (request.settings().size() > SPECS.size()) {
            throw new UserSettingValidationException("too many settings");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        for (Map.Entry<String, Object> entry : request.settings().entrySet()) {
            SettingSpec spec = SPECS.get(entry.getKey());
            if (spec == null) {
                throw new UserSettingValidationException("unknown setting key: " + entry.getKey());
            }
            if (entry.getValue() == null) {
                userSettingMapper.deleteByOwnerAndKey(currentUser.tenantId(), currentUser.userId(), entry.getKey());
                continue;
            }
            userSettingMapper.upsert(row(currentUser, entry.getKey(), spec.toStorage(entry.getValue()), now));
        }
        return new UserSettingsResponse(resolve(currentUser));
    }

    private Map<String, Object> resolve(CurrentUser currentUser) {
        Map<String, Object> resolved = new TreeMap<>();
        SPECS.forEach((key, spec) -> resolved.put(key, spec.defaultValue()));
        List<UserSettingEntity> stored = userSettingMapper.findByOwner(currentUser.tenantId(), currentUser.userId());
        for (UserSettingEntity entity : stored) {
            SettingSpec spec = SPECS.get(entity.getSettingKey());
            if (spec != null) {
                resolved.put(entity.getSettingKey(), spec.fromStorage(entity.getSettingValue()));
            }
        }
        return resolved;
    }

    private UserSettingEntity row(CurrentUser currentUser, String key, String value, LocalDateTime now) {
        UserSettingEntity entity = new UserSettingEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setTenantId(currentUser.tenantId());
        entity.setUserId(currentUser.userId());
        entity.setSettingKey(key);
        entity.setSettingValue(value);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return entity;
    }
}
