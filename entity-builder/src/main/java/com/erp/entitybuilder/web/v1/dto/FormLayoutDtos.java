package com.erp.entitybuilder.web.v1.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class FormLayoutDtos {

    public record FormLayoutDto(
            UUID id,
            UUID tenantId,
            UUID entityId,
            String name,
            @JsonProperty("isDefault") boolean isDefault,
            String status,
            Map<String, Object> layout,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public static class CreateFormLayoutRequest {
        @NotBlank
        @Size(max = 255)
        private String name;

        @NotNull
        private Map<String, Object> layout;

        @JsonProperty("isDefault")
        private boolean isDefault = false;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Map<String, Object> getLayout() { return layout; }
        public void setLayout(Map<String, Object> layout) { this.layout = layout; }
        public boolean isDefault() { return isDefault; }
        public void setDefault(boolean aDefault) { isDefault = aDefault; }
    }

    public static class CreateFromTemplateRequest {
        @NotBlank
        private String templateKey;

        @NotBlank
        @Size(max = 255)
        private String name;

        @JsonProperty("isDefault")
        private boolean isDefault = false;

        public String getTemplateKey() {
            return templateKey;
        }

        public void setTemplateKey(String templateKey) {
            this.templateKey = templateKey;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public boolean isDefault() {
            return isDefault;
        }

        public void setDefault(boolean aDefault) {
            isDefault = aDefault;
        }
    }

    public static class UpdateFormLayoutRequest {
        @Size(max = 255)
        private String name;

        private Map<String, Object> layout;

        @JsonProperty("isDefault")
        private Boolean isDefault;
        private String status;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Map<String, Object> getLayout() { return layout; }
        public void setLayout(Map<String, Object> layout) { this.layout = layout; }
        public Boolean getIsDefault() { return isDefault; }
        public void setDefault(Boolean aDefault) { isDefault = aDefault; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
}

