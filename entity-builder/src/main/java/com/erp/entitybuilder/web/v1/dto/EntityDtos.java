package com.erp.entitybuilder.web.v1.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public class EntityDtos {

    public record EntityDto(
            UUID id,
            UUID tenantId,
            String name,
            String slug,
            String description,
            UUID baseEntityId,
            String defaultDisplayFieldSlug,
            String status,
            String categoryKey,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public static class CreateEntityRequest {
        @NotBlank
        @Size(max = 255)
        private String name;

        @NotBlank
        @Size(max = 100)
        private String slug;

        @Size(max = 500)
        private String description;

        @Size(max = 50)
        private String status = "ACTIVE";

        @Size(max = 64)
        private String categoryKey;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getSlug() { return slug; }
        public void setSlug(String slug) { this.slug = slug; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getCategoryKey() { return categoryKey; }
        public void setCategoryKey(String categoryKey) { this.categoryKey = categoryKey; }
    }

    public static class UpdateEntityRequest {
        @Size(max = 255)
        private String name;

        @Size(max = 100)
        private String slug;

        @Size(max = 500)
        private String description;

        @Size(max = 50)
        private String status;

        /** When true, clears {@code defaultDisplayFieldSlug}. Ignored if {@code defaultDisplayFieldSlug} is also set. */
        private Boolean clearDefaultDisplayField;

        @Size(max = 100)
        private String defaultDisplayFieldSlug;

        /** When true, clears {@code categoryKey}. */
        private Boolean clearCategoryKey;

        @Size(max = 64)
        private String categoryKey;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getSlug() { return slug; }
        public void setSlug(String slug) { this.slug = slug; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Boolean getClearDefaultDisplayField() { return clearDefaultDisplayField; }
        public void setClearDefaultDisplayField(Boolean clearDefaultDisplayField) { this.clearDefaultDisplayField = clearDefaultDisplayField; }
        public String getDefaultDisplayFieldSlug() { return defaultDisplayFieldSlug; }
        public void setDefaultDisplayFieldSlug(String defaultDisplayFieldSlug) { this.defaultDisplayFieldSlug = defaultDisplayFieldSlug; }
        public Boolean getClearCategoryKey() { return clearCategoryKey; }
        public void setClearCategoryKey(Boolean clearCategoryKey) { this.clearCategoryKey = clearCategoryKey; }
        public String getCategoryKey() { return categoryKey; }
        public void setCategoryKey(String categoryKey) { this.categoryKey = categoryKey; }
    }
}

