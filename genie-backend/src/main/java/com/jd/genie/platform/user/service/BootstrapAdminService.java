package com.jd.genie.platform.user.service;

import com.jd.genie.platform.contract.UserRole;
import com.jd.genie.platform.user.config.BootstrapProperties;
import com.jd.genie.platform.user.dto.CreateUserCommand;
import com.jd.genie.platform.user.entity.TenantEntity;
import com.jd.genie.platform.user.entity.UserStatus;
import com.jd.genie.platform.user.mapper.UserMapper;
import org.springframework.stereotype.Service;

@Service
public class BootstrapAdminService {
    private final TenantService tenantService;
    private final UserService userService;
    private final UserMapper userMapper;
    private final BootstrapProperties properties;

    public BootstrapAdminService(TenantService tenantService, UserService userService, UserMapper userMapper,
                                 BootstrapProperties properties) {
        this.tenantService = tenantService;
        this.userService = userService;
        this.userMapper = userMapper;
        this.properties = properties;
    }

    public void initialize(boolean productionProfile) {
        TenantEntity tenant = tenantService.ensureDefaultTenant();
        if (userMapper.countAll() > 0) {
            return;
        }
        if (isBlank(properties.getAdminUsername()) || isBlank(properties.getAdminPassword())) {
            if (productionProfile) {
                throw new IllegalStateException("Bootstrap administrator credentials are required in prod");
            }
            return;
        }
        userService.createUser(new CreateUserCommand(
            tenant.getId(),
            properties.getAdminUsername(),
            "Bootstrap Administrator",
            properties.getAdminPassword(),
            UserRole.ADMIN,
            UserStatus.ACTIVE
        ));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
