package com.erp.globalsearch.security;

import java.util.List;
import java.util.UUID;

public class TenantPrincipal {

    private final UUID userId;
    private final UUID tenantId;
    private final String email;
    private final List<String> roles;
    private final List<String> permissions;

    public TenantPrincipal(UUID userId, UUID tenantId, String email, List<String> roles, List<String> permissions) {
        this.userId = userId;
        this.tenantId = tenantId;
        this.email = email;
        this.roles = roles != null ? List.copyOf(roles) : List.of();
        this.permissions = permissions != null ? List.copyOf(permissions) : List.of();
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getEmail() {
        return email;
    }

    public List<String> getRoles() {
        return roles;
    }

    public List<String> getPermissions() {
        return permissions;
    }
}
