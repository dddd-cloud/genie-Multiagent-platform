package com.jd.genie.platform.user.service;

import com.jd.genie.platform.contract.UserRole;
import com.jd.genie.platform.user.dto.CreateUserCommand;
import com.jd.genie.platform.user.entity.UserEntity;
import com.jd.genie.platform.user.entity.UserStatus;
import com.jd.genie.platform.user.mapper.UserMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {
    private static final Pattern USERNAME_PATTERN = Pattern.compile("[a-z0-9._-]{3,64}");

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public UserService(UserMapper userMapper, PasswordEncoder passwordEncoder, Clock clock) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    public UserEntity createUser(CreateUserCommand command) {
        String tenantId = requireText(command.tenantId(), "tenantId");
        String username = normalizeUsername(command.username());
        String displayName = normalizeDisplayName(command.displayName());
        validatePassword(command.password());
        requireRole(command.role());
        requireStatus(command.status());
        if (userMapper.findByTenantIdAndUsername(tenantId, username) != null) {
            throw new UserAlreadyExistsException(username);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID().toString());
        user.setTenantId(tenantId);
        user.setUsername(username);
        user.setDisplayName(displayName);
        user.setPasswordHash(passwordEncoder.encode(command.password()));
        user.setRole(command.role());
        user.setStatus(command.status());
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        user.setVersion(0);
        userMapper.insert(user);
        return user;
    }

    public String normalizeUsername(String username) {
        String normalized = requireText(username, "username").toLowerCase(Locale.ROOT);
        if (!USERNAME_PATTERN.matcher(normalized).matches()) {
            throw new UserValidationException("username must be 3-64 characters of A-Z, a-z, 0-9, '.', '_' or '-'");
        }
        return normalized;
    }

    private String normalizeDisplayName(String displayName) {
        String normalized = requireText(displayName, "displayName");
        int length = normalized.codePointCount(0, normalized.length());
        if (length < 1 || length > 128) {
            throw new UserValidationException("displayName must contain 1-128 Unicode characters");
        }
        return normalized;
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 8 || password.length() > 64) {
            throw new UserValidationException("password must contain 8-64 characters");
        }
    }

    private String requireText(String value, String field) {
        if (value == null) {
            throw new UserValidationException(field + " is required");
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new UserValidationException(field + " is required");
        }
        return trimmed;
    }

    private void requireRole(UserRole role) {
        if (role == null) {
            throw new UserValidationException("role is required");
        }
    }

    private void requireStatus(UserStatus status) {
        if (status == null) {
            throw new UserValidationException("status is required");
        }
    }
}
