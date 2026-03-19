package com.erp.entitybuilder.web.v1;

import com.erp.entitybuilder.security.TenantPrincipal;
import com.erp.entitybuilder.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtil {
    private SecurityUtil() {}

    public static TenantPrincipal principal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof TenantPrincipal p)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "unauthorized", "Not authenticated");
        }
        return (TenantPrincipal) auth.getPrincipal();
    }

    public static boolean hasAuthority(String authority) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(authority));
    }
}

