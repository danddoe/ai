package com.erp.iam.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component("iamSecurity")
public class IamSecurity {

    public boolean isTenant(UUID tenantId) {
        TenantPrincipal p = principalOrNull();
        return p != null && tenantId != null && tenantId.equals(p.getTenantId());
    }

    public boolean isCrossTenantAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("iam:security:admin") || a.getAuthority().equals("iam:tenants:admin"));
    }

    /** Allow-all for SUPERADMIN: when true, treat as having every IAM permission. */
    public boolean isSuperAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("iam:superadmin"));
    }

    private TenantPrincipal principalOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return null;
        Object p = auth.getPrincipal();
        if (p instanceof TenantPrincipal tp) return tp;
        return null;
    }
}

