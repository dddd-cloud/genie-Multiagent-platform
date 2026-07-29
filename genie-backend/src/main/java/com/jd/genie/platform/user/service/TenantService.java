package com.jd.genie.platform.user.service;

import com.jd.genie.platform.user.entity.TenantEntity;
import com.jd.genie.platform.user.entity.TenantStatus;
import com.jd.genie.platform.user.mapper.TenantMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

@Service
public class TenantService {
    public static final String DEFAULT_TENANT_ID = "tenant-default";
    public static final String DEFAULT_TENANT_CODE = "default";

    private final TenantMapper tenantMapper;
    private final Clock clock;

    public TenantService(TenantMapper tenantMapper, Clock clock) {
        this.tenantMapper = tenantMapper;
        this.clock = clock;
    }

    public TenantEntity ensureDefaultTenant() {
        TenantEntity existing = tenantMapper.findByCode(DEFAULT_TENANT_CODE);
        if (existing != null) {
            return existing;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        TenantEntity tenant = new TenantEntity();
        tenant.setId(DEFAULT_TENANT_ID);
        tenant.setCode(DEFAULT_TENANT_CODE);
        tenant.setName("Default Tenant");
        tenant.setStatus(TenantStatus.ACTIVE);
        tenant.setCreatedAt(now);
        tenant.setUpdatedAt(now);
        tenantMapper.insert(tenant);
        return tenant;
    }
}
