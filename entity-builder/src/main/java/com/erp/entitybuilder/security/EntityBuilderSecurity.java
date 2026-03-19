package com.erp.entitybuilder.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component("entityBuilderSecurity")
public class EntityBuilderSecurity {

    public boolean isTenant(UUID tenantId) {
        TenantPrincipal p = principalOrNull();
        return p != null && tenantId != null && tenantId.equals(p.getTenantId());
    }

    public boolean hasCrossTenantAdminAuthority() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream().anyMatch(a ->
                a.getAuthority().equals("entity_builder:security:admin") ||
                        a.getAuthority().equals("entity_builder:tenants:admin"));
    }

    private TenantPrincipal principalOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return null;
        if (auth.getPrincipal() instanceof TenantPrincipal tp) return tp;
        return null;
    }
}

