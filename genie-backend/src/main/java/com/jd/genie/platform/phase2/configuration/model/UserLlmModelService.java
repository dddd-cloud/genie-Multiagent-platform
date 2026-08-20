package com.jd.genie.platform.phase2.configuration.model;

import com.jd.genie.agent.llm.LLMSettings;
import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2contract.error.Phase2ContractException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserLlmModelService {
    private static final int MAX_NAME = 128;
    private static final int MAX_MODEL = 256;
    private static final int MAX_URL = 2048;
    private static final Pattern NAME_PATTERN = Pattern.compile("^[A-Za-z0-9._-]+$");
    private static final String SELECT_OWNED = """
        SELECT id, tenant_id, owner_id, name, display_name, model, base_url, interface_url,
               max_tokens, temperature, max_input_tokens, api_key_envelope, created_at, updated_at
        FROM user_llm_model
        WHERE tenant_id = ? AND owner_id = ?
        """;

    private final JdbcTemplate jdbc;
    private final LlmApiKeyCipher cipher;
    private final Clock clock = Clock.systemUTC();

    public UserLlmModelService(JdbcTemplate jdbc, LlmApiKeyCipher cipher) {
        this.jdbc = jdbc;
        this.cipher = cipher;
    }

    public List<UserLlmModelRecord> list(CurrentUser user) {
        requireUser(user);
        return jdbc.query(SELECT_OWNED + " ORDER BY created_at ASC, name ASC", this::mapRow, user.tenantId(), user.userId());
    }

    public UserLlmModelRecord get(CurrentUser user, String id) {
        requireUser(user);
        String key = requireId(id);
        List<UserLlmModelRecord> rows = jdbc.query(
            SELECT_OWNED + " AND (id = ? OR name = ?) LIMIT 1",
            this::mapRow,
            user.tenantId(),
            user.userId(),
            key,
            key
        );
        if (rows.isEmpty()) {
            throw notFound();
        }
        return rows.get(0);
    }

    @Transactional
    public List<UserLlmModelRecord> listAndSeed(CurrentUser user, Map<String, LLMSettings> env) {
        List<UserLlmModelRecord> existing = list(user);
        if (!existing.isEmpty() || env == null || env.isEmpty()) {
            return existing;
        }
        for (Map.Entry<String, LLMSettings> entry : env.entrySet()) {
            String name = trim(entry.getKey());
            if (name.isEmpty() || ModelCatalogService.SYSTEM_DEFAULT.equals(name)) {
                continue;
            }
            LLMSettings settings = entry.getValue() == null ? LLMSettings.builder().build() : entry.getValue();
            try {
                insert(
                    user,
                    new LlmModelWriteRequest(
                        name,
                        name,
                        blankToDefault(settings.getModel(), name),
                        nullToEmpty(settings.getBaseUrl()),
                        blankToDefault(settings.getInterfaceUrl(), "/v1/chat/completions"),
                        settings.getMaxTokens() <= 0 ? 16384 : settings.getMaxTokens(),
                        settings.getTemperature(),
                        settings.getMaxInputTokens() <= 0 ? 100000 : settings.getMaxInputTokens(),
                        settings.getApiKey()
                    ),
                    true
                );
            } catch (Phase2ContractException ignored) {
                // Unique race or invalid env row: keep going and re-list.
            }
        }
        return list(user);
    }

    @Transactional
    public UserLlmModelRecord create(CurrentUser user, LlmModelWriteRequest request) {
        requireUser(user);
        return insert(user, request, false);
    }

    @Transactional
    public UserLlmModelRecord update(CurrentUser user, String id, LlmModelWriteRequest request) {
        requireUser(user);
        UserLlmModelRecord current = get(user, id);
        Normalized n = normalize(request, false);
        if (n.apiKey() == null || n.apiKey().isEmpty()) {
            n = n.withApiKey(null);
        }
        String envelope = current.apiKeyEnvelope();
        if (n.apiKey() != null && !n.apiKey().isEmpty()) {
            envelope = cipher.encrypt(user.tenantId(), user.userId(), current.id(), n.apiKey());
        }
        Instant now = Instant.now(clock);
        try {
            int changed = jdbc.update(
                """
                UPDATE user_llm_model
                SET name = ?, display_name = ?, model = ?, base_url = ?, interface_url = ?,
                    max_tokens = ?, temperature = ?, max_input_tokens = ?, api_key_envelope = ?, updated_at = ?
                WHERE id = ? AND tenant_id = ? AND owner_id = ?
                """,
                n.name(),
                n.displayName(),
                n.model(),
                n.baseUrl(),
                n.interfaceUrl(),
                n.maxTokens(),
                n.temperature(),
                n.maxInputTokens(),
                envelope,
                Timestamp.from(now),
                current.id(),
                user.tenantId(),
                user.userId()
            );
            if (changed != 1) {
                throw notFound();
            }
        } catch (DuplicateKeyException ex) {
            throw validation("Model name already exists");
        }
        return get(user, current.id());
    }

    @Transactional
    public void delete(CurrentUser user, String id) {
        requireUser(user);
        UserLlmModelRecord current = get(user, id);
        int changed = jdbc.update(
            "DELETE FROM user_llm_model WHERE id = ? AND tenant_id = ? AND owner_id = ?",
            current.id(),
            user.tenantId(),
            user.userId()
        );
        if (changed != 1) {
            throw notFound();
        }
    }

    public UserLlmModelRecord findByName(String tenantId, String ownerId, String name) {
        if (isBlank(tenantId) || isBlank(ownerId) || isBlank(name)) {
            return null;
        }
        List<UserLlmModelRecord> rows = jdbc.query(
            SELECT_OWNED + " AND name = ? LIMIT 1",
            this::mapRow,
            tenantId,
            ownerId,
            name.trim()
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<UserLlmModelRecord> listByOwner(String tenantId, String ownerId) {
        if (isBlank(tenantId) || isBlank(ownerId)) {
            return List.of();
        }
        return jdbc.query(SELECT_OWNED + " ORDER BY created_at ASC, name ASC", this::mapRow, tenantId, ownerId);
    }

    public String decryptApiKey(UserLlmModelRecord row) {
        if (row == null) {
            return null;
        }
        return cipher.decrypt(row.tenantId(), row.ownerId(), row.id(), row.apiKeyEnvelope());
    }

    public LLMSettings toSettings(UserLlmModelRecord row) {
        if (row == null) {
            return null;
        }
        return LLMSettings.builder()
            .model(row.model())
            .maxTokens(row.maxTokens())
            .temperature(row.temperature())
            .baseUrl(row.baseUrl())
            .interfaceUrl(row.interfaceUrl())
            .apiKey(decryptApiKey(row))
            .maxInputTokens(row.maxInputTokens())
            .functionCallType("function_call")
            .build();
    }

    private UserLlmModelRecord insert(CurrentUser user, LlmModelWriteRequest request, boolean seed) {
        Normalized n = normalize(request, !seed);
        String id = UUID.randomUUID().toString();
        Instant now = Instant.now(clock);
        String envelope = (n.apiKey() == null || n.apiKey().isEmpty())
            ? null
            : cipher.encrypt(user.tenantId(), user.userId(), id, n.apiKey());
        try {
            jdbc.update(
                """
                INSERT INTO user_llm_model(
                    id, tenant_id, owner_id, name, display_name, model, base_url, interface_url,
                    max_tokens, temperature, max_input_tokens, api_key_envelope, created_at, updated_at
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                id,
                user.tenantId(),
                user.userId(),
                n.name(),
                n.displayName(),
                n.model(),
                n.baseUrl(),
                n.interfaceUrl(),
                n.maxTokens(),
                n.temperature(),
                n.maxInputTokens(),
                envelope,
                Timestamp.from(now),
                Timestamp.from(now)
            );
        } catch (DuplicateKeyException ex) {
            throw validation("Model name already exists");
        }
        return get(user, id);
    }

    private Normalized normalize(LlmModelWriteRequest request, boolean requireKey) {
        if (request == null) {
            throw validation("request must not be null");
        }
        String name = trim(request.name());
        if (name.isEmpty() || name.codePointCount(0, name.length()) > MAX_NAME) {
            throw validation("name is required");
        }
        if (ModelCatalogService.SYSTEM_DEFAULT.equalsIgnoreCase(name) || "default".equalsIgnoreCase(name)) {
            throw validation("reserved model name");
        }
        if (!NAME_PATTERN.matcher(name).matches()) {
            throw validation("name is invalid");
        }
        String displayName = trim(request.displayName());
        if (displayName.isEmpty()) {
            displayName = name;
        }
        if (displayName.codePointCount(0, displayName.length()) > MAX_NAME) {
            throw validation("displayName is too long");
        }
        String model = trim(request.model());
        if (model.isEmpty() || model.codePointCount(0, model.length()) > MAX_MODEL) {
            throw validation("model is required");
        }
        String baseUrl = nullToEmpty(request.baseUrl());
        if (baseUrl.length() > MAX_URL) {
            throw validation("baseUrl is too long");
        }
        String interfaceUrl = trim(request.interfaceUrl());
        if (interfaceUrl.isEmpty()) {
            interfaceUrl = "/v1/chat/completions";
        }
        if (interfaceUrl.length() > 512) {
            throw validation("interfaceUrl is too long");
        }
        int maxTokens = request.maxTokens() == null ? 16384 : request.maxTokens();
        if (maxTokens < 1 || maxTokens > 200_000) {
            throw validation("maxTokens is out of range");
        }
        double temperature = request.temperature() == null ? 0d : request.temperature();
        if (temperature < 0 || temperature > 2) {
            throw validation("temperature is out of range");
        }
        int maxInputTokens = request.maxInputTokens() == null ? 100_000 : request.maxInputTokens();
        if (maxInputTokens < 1 || maxInputTokens > 2_000_000) {
            throw validation("maxInputTokens is out of range");
        }
        String apiKey = request.apiKey() == null ? "" : request.apiKey();
        if (requireKey && apiKey.isBlank()) {
            throw validation("apiKey is required");
        }
        if (apiKey.length() > 4096) {
            throw validation("apiKey is too long");
        }
        return new Normalized(name, displayName, model, baseUrl.trim(), interfaceUrl, maxTokens, temperature, maxInputTokens, apiKey);
    }

    private UserLlmModelRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp created = rs.getTimestamp("created_at");
        Timestamp updated = rs.getTimestamp("updated_at");
        return new UserLlmModelRecord(
            rs.getString("id"),
            rs.getString("tenant_id"),
            rs.getString("owner_id"),
            rs.getString("name"),
            rs.getString("display_name"),
            rs.getString("model"),
            rs.getString("base_url"),
            rs.getString("interface_url"),
            rs.getInt("max_tokens"),
            rs.getDouble("temperature"),
            rs.getInt("max_input_tokens"),
            rs.getString("api_key_envelope"),
            created == null ? Instant.EPOCH : created.toInstant(),
            updated == null ? Instant.EPOCH : updated.toInstant()
        );
    }

    private void requireUser(CurrentUser user) {
        if (user == null || isBlank(user.tenantId()) || isBlank(user.userId())) {
            throw new Phase2ContractException(MvpErrorCode.AUTH_REQUIRED, "Authentication required");
        }
    }

    private String requireId(String id) {
        String value = trim(id);
        if (value.isEmpty() || value.length() > 128) {
            throw notFound();
        }
        return value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static String blankToDefault(String value, String fallback) {
        String trimmed = trim(value);
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    private static Phase2ContractException notFound() {
        return new Phase2ContractException(MvpErrorCode.RESOURCE_NOT_FOUND, "Model not found");
    }

    private static Phase2ContractException validation(String message) {
        return new Phase2ContractException(MvpErrorCode.VALIDATION_ERROR, message);
    }

    private record Normalized(
        String name,
        String displayName,
        String model,
        String baseUrl,
        String interfaceUrl,
        int maxTokens,
        double temperature,
        int maxInputTokens,
        String apiKey
    ) {
        Normalized withApiKey(String next) {
            return new Normalized(name, displayName, model, baseUrl, interfaceUrl, maxTokens, temperature, maxInputTokens, next);
        }
    }
}
