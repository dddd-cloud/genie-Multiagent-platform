package com.jd.genie.platform.user.service;

import com.jd.genie.platform.contract.UserRole;
import com.jd.genie.platform.user.dto.CreateUserCommand;
import com.jd.genie.platform.user.entity.TenantEntity;
import com.jd.genie.platform.user.entity.UserEntity;
import com.jd.genie.platform.user.entity.UserStatus;
import com.jd.genie.platform.user.mapper.UserMapper;
import org.springframework.core.env.Environment;

/** Seeds the fixed, profile-scoped identities required by MVP acceptance. */
public class AcceptanceUserSeeder {
    static final String ADMIN_PASSWORD_PROPERTY = "MVP_ACCEPTANCE_ADMIN_PASSWORD";
    static final String USER_PASSWORD_PROPERTY = "MVP_ACCEPTANCE_USER_PASSWORD";

    private final TenantService tenantService;
    private final UserService userService;
    private final UserMapper userMapper;
    private final Environment environment;

    public AcceptanceUserSeeder(TenantService tenantService, UserService userService, UserMapper userMapper,
                                Environment environment) {
        this.tenantService = tenantService;
        this.userService = userService;
        this.userMapper = userMapper;
        this.environment = environment;
    }

    public void seed() {
        TenantEntity tenant = tenantService.ensureDefaultTenant();
        if (!TenantService.DEFAULT_TENANT_ID.equals(tenant.getId())) {
            throw new IllegalStateException("Default tenant id must be " + TenantService.DEFAULT_TENANT_ID);
        }

        String adminPassword = requiredPassword(ADMIN_PASSWORD_PROPERTY);
        String userPassword = requiredPassword(USER_PASSWORD_PROPERTY);
        ensureUser(tenant.getId(), "admin", "Acceptance Admin", adminPassword, UserRole.ADMIN);
        ensureUser(tenant.getId(), "user-a", "Acceptance User A", userPassword, UserRole.USER);
        ensureUser(tenant.getId(), "user-b", "Acceptance User B", userPassword, UserRole.USER);
    }

    private void ensureUser(String tenantId, String username, String displayName, String password, UserRole role) {
        UserEntity existing = userMapper.findByTenantIdAndUsername(tenantId, username);
        if (existing == null) {
            UserEntity sameUsername = userMapper.findActiveByUsername(username);
            if (sameUsername != null && !tenantId.equals(sameUsername.getTenantId())) {
                throw new IllegalStateException("Acceptance user " + username + " has incompatible tenant, role, or status");
            }
            userService.createUser(new CreateUserCommand(tenantId, username, displayName, password, role, UserStatus.ACTIVE));
            return;
        }
        if (!tenantId.equals(existing.getTenantId()) || existing.getRole() != role || existing.getStatus() != UserStatus.ACTIVE) {
            throw new IllegalStateException("Acceptance user " + username + " has incompatible tenant, role, or status");
        }
    }

    private String requiredPassword(String propertyName) {
        String value = environment.getProperty(propertyName);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(propertyName + " is required");
        }
        return value;
    }
}
