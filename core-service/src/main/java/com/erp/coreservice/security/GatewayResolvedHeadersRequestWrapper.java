package com.erp.coreservice.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * Hides {@code X-Resolved-*} from downstream unless {@code X-Gateway-Trusted} matches the configured secret.
 */
final class GatewayResolvedHeadersRequestWrapper extends HttpServletRequestWrapper {

    private static final String TRUST = "X-Gateway-Trusted";
    private static final Set<String> RESOLVED = Set.of(
            "X-Resolved-Tenant-Id",
            "X-Resolved-Company-Id",
            "X-Resolved-Default-Bu-Id"
    );

    private final boolean trustResolvedHeaders;

    GatewayResolvedHeadersRequestWrapper(HttpServletRequest request, String trustSecret) {
        super(request);
        String sent = request.getHeader(TRUST);
        this.trustResolvedHeaders = trustSecret != null
                && !trustSecret.isEmpty()
                && sent != null
                && constantTimeEquals(trustSecret, sent);
    }

    @Override
    public String getHeader(String name) {
        if (!trustResolvedHeaders && isResolved(name)) {
            return null;
        }
        return super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        if (!trustResolvedHeaders && isResolved(name)) {
            return Collections.emptyEnumeration();
        }
        return super.getHeaders(name);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        if (trustResolvedHeaders) {
            return super.getHeaderNames();
        }
        List<String> names = Collections.list(super.getHeaderNames());
        names.removeIf(n -> isResolved(n) || TRUST.equalsIgnoreCase(n));
        return Collections.enumeration(names);
    }

    private static boolean isResolved(String name) {
        if (name == null) {
            return false;
        }
        for (String r : RESOLVED) {
            if (r.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean constantTimeEquals(String a, String b) {
        byte[] aB = a.getBytes(StandardCharsets.UTF_8);
        byte[] bB = b.getBytes(StandardCharsets.UTF_8);
        if (aB.length != bB.length) {
            return false;
        }
        return MessageDigest.isEqual(aB, bB);
    }
}
