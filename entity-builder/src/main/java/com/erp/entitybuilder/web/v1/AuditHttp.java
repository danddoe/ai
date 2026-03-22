package com.erp.entitybuilder.web.v1;

import java.util.UUID;

final class AuditHttp {

    private AuditHttp() {}

    static UUID parseCorrelationId(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(header.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
