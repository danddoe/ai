package com.erp.entitybuilder.web.v1.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

/**
 * One row from {@code audit_log} for API responses.
 */
public class AuditEventDtos {

    public static class AuditEventDto {
        private UUID id;
        private Instant createdAt;
        private UUID actorId;
        /** Resolved from IAM {@code users} / {@code tenant_users} when present in the same database; otherwise null. */
        private String actorLabel;
        private String action;
        private String operation;
        private String resourceType;
        private UUID resourceId;
        private UUID correlationId;
        private String sourceService;
        private JsonNode payload;

        public UUID getId() {
            return id;
        }

        public void setId(UUID id) {
            this.id = id;
        }

        public Instant getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(Instant createdAt) {
            this.createdAt = createdAt;
        }

        public UUID getActorId() {
            return actorId;
        }

        public void setActorId(UUID actorId) {
            this.actorId = actorId;
        }

        public String getActorLabel() {
            return actorLabel;
        }

        public void setActorLabel(String actorLabel) {
            this.actorLabel = actorLabel;
        }

        public String getAction() {
            return action;
        }

        public void setAction(String action) {
            this.action = action;
        }

        public String getOperation() {
            return operation;
        }

        public void setOperation(String operation) {
            this.operation = operation;
        }

        public String getResourceType() {
            return resourceType;
        }

        public void setResourceType(String resourceType) {
            this.resourceType = resourceType;
        }

        public UUID getResourceId() {
            return resourceId;
        }

        public void setResourceId(UUID resourceId) {
            this.resourceId = resourceId;
        }

        public UUID getCorrelationId() {
            return correlationId;
        }

        public void setCorrelationId(UUID correlationId) {
            this.correlationId = correlationId;
        }

        public String getSourceService() {
            return sourceService;
        }

        public void setSourceService(String sourceService) {
            this.sourceService = sourceService;
        }

        public JsonNode getPayload() {
            return payload;
        }

        public void setPayload(JsonNode payload) {
            this.payload = payload;
        }
    }
}
