package com.erp.loansmodule.web.v1;

import com.erp.loansmodule.security.TenantPrincipal;
import com.erp.loansmodule.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class LoansModuleSecurityUtil {
    private LoansModuleSecurityUtil() {}

    public static TenantPrincipal principal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof TenantPrincipal)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "unauthorized", "Not authenticated");
        }
        return (TenantPrincipal) auth.getPrincipal();
    }
}
