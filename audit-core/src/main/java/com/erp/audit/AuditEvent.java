package com.erp.audit;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable row to be inserted into {@code audit_log}.
 */
public final class AuditEvent {

    public static final int PAYLOAD_SCHEMA_VERSION = 1;

    private final UUID tenantId;
    private final UUID actorId;
    private final String sourceService;
    private final String operation;
    private final String action;
    private final String resourceType;
    private final UUID resourceId;
    private final UUID correlationId;
    private final Map<String, Object> payload;

    private AuditEvent(Builder b) {
        this.tenantId = b.tenantId;
        this.actorId = b.actorId;
        this.sourceService = Objects.requireNonNull(b.sourceService, "sourceService");
        this.operation = Objects.requireNonNull(b.operation, "operation");
        this.action = Objects.requireNonNull(b.action, "action");
        this.resourceType = b.resourceType;
        this.resourceId = b.resourceId;
        this.correlationId = b.correlationId;
        this.payload = Collections.unmodifiableMap(new LinkedHashMap<>(b.payload));
    }

    public UUID tenantId() {
        return tenantId;
    }

    public UUID actorId() {
        return actorId;
    }

    public String sourceService() {
        return sourceService;
    }

    public String operation() {
        return operation;
    }

    public String action() {
        return action;
    }

    public String resourceType() {
        return resourceType;
    }

    public UUID resourceId() {
        return resourceId;
    }

    public UUID correlationId() {
        return correlationId;
    }

    public Map<String, Object> payload() {
        return payload;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private UUID tenantId;
        private UUID actorId;
        private String sourceService;
        private String operation;
        private String action;
        private String resourceType;
        private UUID resourceId;
        private UUID correlationId;
        private final Map<String, Object> payload = new LinkedHashMap<>();

        public Builder tenantId(UUID tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder actorId(UUID actorId) {
            this.actorId = actorId;
            return this;
        }

        public Builder sourceService(String sourceService) {
            this.sourceService = sourceService;
            return this;
        }

        public Builder operation(String operation) {
            this.operation = operation;
            return this;
        }

        public Builder action(String action) {
            this.action = action;
            return this;
        }

        public Builder resourceType(String resourceType) {
            this.resourceType = resourceType;
            return this;
        }

        public Builder resourceId(UUID resourceId) {
            this.resourceId = resourceId;
            return this;
        }

        public Builder correlationId(UUID correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public Builder putPayload(String key, Object value) {
            this.payload.put(key, value);
            return this;
        }

        public Builder payload(Map<String, Object> map) {
            this.payload.clear();
            if (map != null) {
                this.payload.putAll(map);
            }
            return this;
        }

        public AuditEvent build() {
            if (!payload.containsKey("schemaVersion")) {
                payload.put("schemaVersion", PAYLOAD_SCHEMA_VERSION);
            }
            return new AuditEvent(this);
        }
    }
}
