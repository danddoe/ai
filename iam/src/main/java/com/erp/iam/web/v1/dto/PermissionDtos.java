package com.erp.iam.web.v1.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public class PermissionDtos {

    public record PermissionDto(
            UUID id,
            String code,
            String description,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public static class CreatePermissionRequest {
        @NotBlank
        @Size(max = 100)
        private String code;

        @Size(max = 500)
        private String description;

        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }

    public static class UpdatePermissionRequest {
        @Size(max = 100)
        private String code;

        @Size(max = 500)
        private String description;

        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }
}

