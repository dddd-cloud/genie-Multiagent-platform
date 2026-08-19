package com.jd.genie.platform.user.service;

import com.jd.genie.platform.contract.PageResponse;
import com.jd.genie.platform.contract.UserRole;
import com.jd.genie.platform.security.SessionRevocationService;
import com.jd.genie.platform.user.dto.AdminUserResponse;
import com.jd.genie.platform.user.dto.CreateAdminUserRequest;
import com.jd.genie.platform.user.entity.UserEntity;
import com.jd.genie.platform.user.entity.UserStatus;
import com.jd.genie.platform.user.mapper.UserMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminUserService {
    private final UserService userService;
    private final UserMapper userMapper;
    private final SessionRevocationService sessionRevocationService;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public AdminUserService(UserService userService, UserMapper userMapper, SessionRevocationService sessionRevocationService,
                            PasswordEncoder passwordEncoder, Clock clock) {
        this.userService = userService; this.userMapper = userMapper; this.sessionRevocationService = sessionRevocationService;
        this.passwordEncoder = passwordEncoder; this.clock = clock;
    }

    private static final int MAX_KEYWORD_LENGTH = 64;

    public PageResponse<AdminUserResponse> list(String tenantId, int page, int pageSize,
                                                String keyword, String role, String status) {
        if (page < 1 || pageSize < 1 || pageSize > 100) throw new UserValidationException("invalid page");
        List<UserEntity> rows = userMapper.searchByTenant(tenantId, keywordPattern(keyword),
            parse(role, UserRole.class, "role"), parse(status, UserStatus.class, "status"),
            (page - 1) * pageSize, pageSize + 1);
        boolean hasMore = rows.size() > pageSize;
        if (hasMore) rows = rows.subList(0, pageSize);
        return new PageResponse<>(rows.stream().map(this::response).toList(), page, pageSize, hasMore);
    }

    public AdminUserResponse get(String tenantId, String userId) {
        return response(requireUser(tenantId, userId));
    }

    /** Escapes LIKE metacharacters so an admin searching for "a_b" does not match "axb". */
    private static String keywordPattern(String keyword) {
        if (keyword == null || keyword.isBlank()) return null;
        String trimmed = keyword.trim();
        if (trimmed.length() > MAX_KEYWORD_LENGTH) throw new UserValidationException("keyword is too long");
        String escaped = trimmed.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }

    private static <E extends Enum<E>> String parse(String raw, Class<E> type, String field) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return Enum.valueOf(type, raw.trim()).name();
        } catch (IllegalArgumentException ex) {
            throw new UserValidationException("invalid " + field);
        }
    }

    @Transactional
    public AdminUserResponse create(String tenantId, CreateAdminUserRequest request) {
        if (request == null) throw new UserValidationException("request is required");
        try {
            return response(userService.createUser(new com.jd.genie.platform.user.dto.CreateUserCommand(
                tenantId, request.username(), request.displayName(), request.password(), request.role(), UserStatus.ACTIVE)));
        } catch (DuplicateKeyException ex) { throw new UserAlreadyExistsException(request.username()); }
    }

    @Transactional
    public AdminUserResponse updateStatus(String tenantId, String userId, UserStatus status) {
        if (status == null) throw new UserValidationException("status is required");
        UserEntity user = requireUser(tenantId, userId);
        if (userMapper.updateStatusByIdAndTenantId(userId, tenantId, status.name(), LocalDateTime.now(clock)) != 1) throw new UserNotFoundException();
        if (status == UserStatus.DISABLED) sessionRevocationService.revokeByUsername(user.getUsername());
        return response(requireUser(tenantId, userId));
    }

    @Transactional
    public void resetPassword(String tenantId, String userId, String newPassword) {
        UserEntity user = requireUser(tenantId, userId);
        if (newPassword == null || newPassword.length() < 8 || newPassword.length() > 64) throw new UserValidationException("newPassword must contain 8-64 characters");
        if (userMapper.updatePasswordByIdAndTenantId(userId, tenantId, passwordEncoder.encode(newPassword), LocalDateTime.now(clock)) != 1) throw new UserNotFoundException();
        sessionRevocationService.revokeByUsername(user.getUsername());
    }

    private UserEntity requireUser(String tenantId, String userId) {
        UserEntity user = userMapper.findByIdAndTenantId(userId, tenantId);
        if (user == null) throw new UserNotFoundException();
        return user;
    }
    private AdminUserResponse response(UserEntity user) {
        return new AdminUserResponse(user.getId(), user.getUsername(), user.getDisplayName(), user.getRole(), user.getStatus(),
            user.getCreatedAt().toInstant(ZoneOffset.UTC).toString(), user.getUpdatedAt().toInstant(ZoneOffset.UTC).toString());
    }
}
