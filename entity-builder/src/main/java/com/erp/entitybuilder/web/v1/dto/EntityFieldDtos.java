package com.erp.entitybuilder.web.v1.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class EntityFieldDtos {

    public record EntityFieldDto(
            UUID id,
            UUID entityId,
            String name,
            String slug,
            String fieldType,
            boolean required,
            boolean pii,
            String status,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public static class CreateFieldRequest {
        @NotBlank
        @Size(max = 255)
        private String name;

        @NotBlank
        @Size(max = 100)
        private String slug;

        @NotBlank
        @Size(max = 50)
        private String fieldType;

        private boolean required = false;
        private boolean pii = false;

        // Free-form JSONB config for validators/options; stored as JSON
        private Map<String, Object> config;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getSlug() { return slug; }
        public void setSlug(String slug) { this.slug = slug; }
        public String getFieldType() { return fieldType; }
        public void setFieldType(String fieldType) { this.fieldType = fieldType; }
        public boolean isRequired() { return required; }
        public void setRequired(boolean required) { this.required = required; }
        public boolean isPii() { return pii; }
        public void setPii(boolean pii) { this.pii = pii; }
        public Map<String, Object> getConfig() { return config; }
        public void setConfig(Map<String, Object> config) { this.config = config; }
    }

    public static class UpdateFieldRequest {
        @Size(max = 255)
        private String name;

        @Size(max = 100)
        private String slug;

        @Size(max = 50)
        private String fieldType;

        private Boolean required;
        private Boolean pii;

        private Map<String, Object> config;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getSlug() { return slug; }
        public void setSlug(String slug) { this.slug = slug; }
        public String getFieldType() { return fieldType; }
        public void setFieldType(String fieldType) { this.fieldType = fieldType; }
        public Boolean getRequired() { return required; }
        public void setRequired(Boolean required) { this.required = required; }
        public Boolean getPii() { return pii; }
        public void setPii(Boolean pii) { this.pii = pii; }
        public Map<String, Object> getConfig() { return config; }
        public void setConfig(Map<String, Object> config) { this.config = config; }
    }
}

