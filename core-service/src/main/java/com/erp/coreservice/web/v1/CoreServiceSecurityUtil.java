package com.erp.coreservice.web.v1;

import com.erp.coreservice.security.TenantPrincipal;
import com.erp.coreservice.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CoreServiceSecurityUtil {
    private CoreServiceSecurityUtil() {
    }

    public static TenantPrincipal principal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof TenantPrincipal)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "unauthorized", "Not authenticated");
        }
        return (TenantPrincipal) auth.getPrincipal();
    }
}
