package com.jd.genie.platform.security;

import com.jd.genie.platform.contract.UserRole;
import com.jd.genie.platform.user.entity.UserEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public final class GenieUserPrincipal implements UserDetails {
    private final String tenantId;
    private final String userId;
    private final String username;
    private final String displayName;
    private final String passwordHash;
    private final UserRole role;

    private GenieUserPrincipal(UserEntity user) {
        this.tenantId = user.getTenantId();
        this.userId = user.getId();
        this.username = user.getUsername();
        this.displayName = user.getDisplayName();
        this.passwordHash = user.getPasswordHash();
        this.role = user.getRole();
    }

    public static GenieUserPrincipal from(UserEntity user) { return new GenieUserPrincipal(user); }
    public String getTenantId() { return tenantId; }
    public String getUserId() { return userId; }
    public String getDisplayName() { return displayName; }
    public UserRole getRole() { return role; }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return List.of(new SimpleGrantedAuthority("ROLE_" + role.name())); }
    @Override public String getPassword() { return passwordHash; }
    @Override public String getUsername() { return username; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return true; }
}
