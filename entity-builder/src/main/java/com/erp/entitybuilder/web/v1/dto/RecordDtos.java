package com.erp.entitybuilder.web.v1.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class RecordDtos {

    public record LinkDto(String relationshipSlug, UUID toRecordId) {}

    public record RecordDto(
            UUID id,
            UUID tenantId,
            UUID entityId,
            String externalId,
            String businessDocumentNumber,
            UUID createdBy,
            UUID updatedBy,
            String status,
            Map<String, Object> values,
            List<LinkDto> links,
            Instant createdAt,
            Instant updatedAt,
            String createdByLabel,
            String updatedByLabel,
            UUID entityStatusId,
            String entityStatusDisplayLabel
    ) {}

    public record RecordLookupItemDto(UUID recordId, String displayLabel, Map<String, Object> values) {}

    public record RecordLookupResponse(List<RecordLookupItemDto> items) {}

    public static class CreateRecordRequest {
        @NotNull
        private Map<String, Object> values;
        private List<LinkInput> links;
        private String externalId;
        private String businessDocumentNumber;

        public Map<String, Object> getValues() { return values; }
        public void setValues(Map<String, Object> values) { this.values = values; }
        public List<LinkInput> getLinks() { return links; }
        public void setLinks(List<LinkInput> links) { this.links = links; }
        public String getExternalId() { return externalId; }
        public void setExternalId(String externalId) { this.externalId = externalId; }
        public String getBusinessDocumentNumber() { return businessDocumentNumber; }
        public void setBusinessDocumentNumber(String businessDocumentNumber) { this.businessDocumentNumber = businessDocumentNumber; }
    }

    public static class UpdateRecordRequest {
        private Map<String, Object> values;
        private List<LinkInput> links;

        public Map<String, Object> getValues() { return values; }
        public void setValues(Map<String, Object> values) { this.values = values; }
        public List<LinkInput> getLinks() { return links; }
        public void setLinks(List<LinkInput> links) { this.links = links; }
    }

    public static class LinkInput {
        @NotNull
        private String relationshipSlug;

        @NotNull
        private UUID toRecordId;

        public String getRelationshipSlug() { return relationshipSlug; }
        public void setRelationshipSlug(String relationshipSlug) { this.relationshipSlug = relationshipSlug; }
        public UUID getToRecordId() { return toRecordId; }
        public void setToRecordId(UUID toRecordId) { this.toRecordId = toRecordId; }
    }
}

