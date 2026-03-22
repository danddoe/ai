package com.erp.gateway.portal;

public final class PortalHostnameNormalizer {

    private PortalHostnameNormalizer() {
    }

    /** Match core-service portal hostname normalization rules. */
    public static String normalize(String host) {
        if (host == null) {
            return null;
        }
        String h = host.trim().toLowerCase();
        if (h.isEmpty()) {
            return null;
        }
        if (h.startsWith("[")) {
            int end = h.indexOf(']');
            if (end > 0) {
                return h.substring(0, end + 1);
            }
        } else {
            int colon = h.lastIndexOf(':');
            if (colon > 0 && h.indexOf(':') == colon) {
                h = h.substring(0, colon);
            }
        }
        return h;
    }
}
