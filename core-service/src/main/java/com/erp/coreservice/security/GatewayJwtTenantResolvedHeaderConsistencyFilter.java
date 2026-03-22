package com.erp.coreservice.security;

import com.erp.coreservice.config.GatewayTrustProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * When the gateway forwards trusted {@code X-Resolved-Tenant-Id}, it must match the JWT tenant.
 * Registered in {@link com.erp.coreservice.config.SecurityConfig} after {@link JwtAuthenticationFilter}.
 */
public class GatewayJwtTenantResolvedHeaderConsistencyFilter extends OncePerRequestFilter {

    private static final String RESOLVED_TENANT = "X-Resolved-Tenant-Id";

    private final GatewayTrustProperties gatewayTrustProperties;

    public GatewayJwtTenantResolvedHeaderConsistencyFilter(GatewayTrustProperties gatewayTrustProperties) {
        this.gatewayTrustProperties = gatewayTrustProperties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String secret = gatewayTrustProperties.getTrustSecret();
        String trust = request.getHeader("X-Gateway-Trusted");
        if (secret == null || secret.isEmpty() || trust == null || !constantTimeEquals(secret, trust)) {
            filterChain.doFilter(request, response);
            return;
        }
        String resolved = request.getHeader(RESOLVED_TENANT);
        if (resolved == null || resolved.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }
        Object p = auth.getPrincipal();
        if (!(p instanceof TenantPrincipal tp)) {
            filterChain.doFilter(request, response);
            return;
        }
        UUID resolvedId;
        try {
            resolvedId = UUID.fromString(resolved.trim());
        } catch (IllegalArgumentException e) {
            response.sendError(HttpStatus.BAD_REQUEST.value(), "Invalid X-Resolved-Tenant-Id");
            return;
        }
        if (!tp.getTenantId().equals(resolvedId)) {
            response.sendError(HttpStatus.FORBIDDEN.value(), "Tenant does not match portal host binding");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static boolean constantTimeEquals(String a, String b) {
        byte[] aB = a.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] bB = b.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (aB.length != bB.length) {
            return false;
        }
        return java.security.MessageDigest.isEqual(aB, bB);
    }
}
