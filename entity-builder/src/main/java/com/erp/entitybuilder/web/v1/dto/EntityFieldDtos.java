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
            int sortOrder,
            String labelOverride,
            String formatString,
            String status,
            Instant createdAt,
            Instant updatedAt,
            Map<String, Object> config
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

        private Integer sortOrder;

        @Size(max = 255)
        private String labelOverride;

        @Size(max = 500)
        private String formatString;

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
        public Integer getSortOrder() { return sortOrder; }
        public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
        public String getLabelOverride() { return labelOverride; }
        public void setLabelOverride(String labelOverride) { this.labelOverride = labelOverride; }
        public String getFormatString() { return formatString; }
        public void setFormatString(String formatString) { this.formatString = formatString; }
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

        private Integer sortOrder;

        @Size(max = 255)
        private String labelOverride;

        @Size(max = 500)
        private String formatString;

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
        public Integer getSortOrder() { return sortOrder; }
        public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
        public String getLabelOverride() { return labelOverride; }
        public void setLabelOverride(String labelOverride) { this.labelOverride = labelOverride; }
        public String getFormatString() { return formatString; }
        public void setFormatString(String formatString) { this.formatString = formatString; }
    }
}

