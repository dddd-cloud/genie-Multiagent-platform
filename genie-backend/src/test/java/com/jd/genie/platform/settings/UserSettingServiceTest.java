package com.jd.genie.platform.settings;

import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.UserRole;
import com.jd.genie.platform.settings.dto.UpdateUserSettingsRequest;
import com.jd.genie.platform.settings.entity.UserSettingEntity;
import com.jd.genie.platform.settings.mapper.UserSettingMapper;
import com.jd.genie.platform.settings.service.UserSettingService;
import com.jd.genie.platform.settings.service.UserSettingValidationException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserSettingServiceTest {

    private static final CurrentUser USER =
        new CurrentUser("tenant-1", "user-1", "alice", "Alice", UserRole.USER);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-02T03:04:05Z"), ZoneOffset.UTC);

    private final RecordingMapper mapper = new RecordingMapper();
    private final UserSettingService service = new UserSettingService(mapper, CLOCK);

    @Test
    void getReturnsEveryWhitelistedKeyWithDefaults() {
        Map<String, Object> settings = service.get(USER).settings();

        assertEquals("AUTO", settings.get("defaultExecutionMode"));
        assertEquals(Boolean.FALSE, settings.get("defaultDeepThink"));
        assertEquals("", settings.get("preferredModelName"));
        assertEquals("BATCHED", settings.get("streamRenderMode"));
        assertEquals("zh-CN", settings.get("locale"));
        assertEquals(7, settings.size());
    }

    @Test
    void updatePersistsWhitelistedValuesAndReturnsMergedView() {
        Map<String, Object> patch = new HashMap<>();
        patch.put("defaultExecutionMode", "ORCHESTRATED");
        patch.put("defaultDeepThink", true);
        patch.put("preferredModelName", "qwen3.7-max");

        Map<String, Object> settings = service.update(USER, new UpdateUserSettingsRequest(patch)).settings();

        assertEquals("ORCHESTRATED", settings.get("defaultExecutionMode"));
        assertEquals(Boolean.TRUE, settings.get("defaultDeepThink"));
        assertEquals("qwen3.7-max", settings.get("preferredModelName"));
        assertEquals("true", mapper.stored.get("defaultDeepThink"));
        assertTrue(mapper.deleted.isEmpty());
    }

    @Test
    void nullValueRemovesTheStoredOverrideSoTheDefaultApplies() {
        service.update(USER, new UpdateUserSettingsRequest(Map.of("locale", "en-US")));
        assertEquals("en-US", service.get(USER).settings().get("locale"));

        Map<String, Object> reset = new HashMap<>();
        reset.put("locale", null);
        Map<String, Object> settings = service.update(USER, new UpdateUserSettingsRequest(reset)).settings();

        assertEquals("zh-CN", settings.get("locale"));
        assertEquals(List.of("locale"), mapper.deleted);
    }

    @Test
    void unknownKeyIsRejectedInsteadOfSilentlyStored() {
        Map<String, Object> patch = Map.of("defaultExcutionMode", "AUTO");

        assertThrows(UserSettingValidationException.class,
            () -> service.update(USER, new UpdateUserSettingsRequest(patch)));
        assertTrue(mapper.stored.isEmpty());
    }

    @Test
    void valueOutsideTheAllowedSetIsRejected() {
        assertThrows(UserSettingValidationException.class, () -> service.update(USER,
            new UpdateUserSettingsRequest(Map.of("defaultExecutionMode", "MAGIC"))));
        assertThrows(UserSettingValidationException.class, () -> service.update(USER,
            new UpdateUserSettingsRequest(Map.of("defaultDeepThink", "yes"))));
        assertThrows(UserSettingValidationException.class, () -> service.update(USER,
            new UpdateUserSettingsRequest(Map.of("preferredModelName", "m".repeat(129)))));
    }

    @Test
    void emptyOrMissingSettingsIsRejected() {
        assertThrows(UserSettingValidationException.class, () -> service.update(USER, null));
        assertThrows(UserSettingValidationException.class,
            () -> service.update(USER, new UpdateUserSettingsRequest(null)));
        assertThrows(UserSettingValidationException.class,
            () -> service.update(USER, new UpdateUserSettingsRequest(Map.of())));
    }

    @Test
    void storedValueFromAnUnknownKeyIsIgnoredWhenReading() {
        mapper.seed("legacyFlag", "true");

        assertFalse(service.get(USER).settings().containsKey("legacyFlag"));
    }

    private static final class RecordingMapper implements UserSettingMapper {
        private final Map<String, String> stored = new HashMap<>();
        private final List<String> deleted = new ArrayList<>();

        void seed(String key, String value) {
            stored.put(key, value);
        }

        @Override
        public List<UserSettingEntity> findByOwner(String tenantId, String userId) {
            assertEquals(USER.tenantId(), tenantId);
            assertEquals(USER.userId(), userId);
            List<UserSettingEntity> rows = new ArrayList<>();
            stored.forEach((key, value) -> {
                UserSettingEntity entity = new UserSettingEntity();
                entity.setSettingKey(key);
                entity.setSettingValue(value);
                rows.add(entity);
            });
            return rows;
        }

        @Override
        public int upsert(UserSettingEntity setting) {
            assertEquals(USER.tenantId(), setting.getTenantId());
            assertEquals(USER.userId(), setting.getUserId());
            stored.put(setting.getSettingKey(), setting.getSettingValue());
            return 1;
        }

        @Override
        public int deleteByOwnerAndKey(String tenantId, String userId, String settingKey) {
            deleted.add(settingKey);
            return stored.remove(settingKey) == null ? 0 : 1;
        }
    }
}
