package com.erp.coreservice.audit;

import com.erp.coreservice.security.TenantPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

/**
 * Resolves audit context from the current HTTP request / security context (same thread as the controller).
 */
public final class CoreAuditInvocationContext {

    private CoreAuditInvocationContext() {
    }

    public static UUID actorIdOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof TenantPrincipal p)) {
            return null;
        }
        return p.getUserId();
    }

    public static UUID correlationIdOrNull() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (!(attrs instanceof ServletRequestAttributes sra)) {
            return null;
        }
        HttpServletRequest req = sra.getRequest();
        String h = req.getHeader("X-Correlation-Id");
        if (h == null || h.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(h.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
