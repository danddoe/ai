package com.erp.entitybuilder.web.v1.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class RecordListViewDtos {

    public record RecordListViewDto(
            UUID id,
            UUID tenantId,
            UUID entityId,
            String name,
            @JsonProperty("isDefault") boolean isDefault,
            String status,
            Map<String, Object> definition,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public static class CreateRecordListViewRequest {
        @NotBlank
        @Size(max = 255)
        private String name;

        @NotNull
        private Map<String, Object> definition;

        @JsonProperty("isDefault")
        private boolean isDefault = false;

        /** ACTIVE (published) or WIP; omit for ACTIVE. */
        private String status;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Map<String, Object> getDefinition() { return definition; }
        public void setDefinition(Map<String, Object> definition) { this.definition = definition; }
        public boolean isDefault() { return isDefault; }
        public void setDefault(boolean aDefault) { isDefault = aDefault; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    public static class UpdateRecordListViewRequest {
        @Size(max = 255)
        private String name;

        private Map<String, Object> definition;

        @JsonProperty("isDefault")
        private Boolean isDefault;
        private String status;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Map<String, Object> getDefinition() { return definition; }
        public void setDefinition(Map<String, Object> definition) { this.definition = definition; }
        public Boolean getIsDefault() { return isDefault; }
        public void setDefault(Boolean aDefault) { isDefault = aDefault; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
}
