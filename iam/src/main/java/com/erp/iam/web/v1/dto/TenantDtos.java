package com.erp.iam.web.v1.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public class TenantDtos {

    public record TenantDto(
            UUID id,
            String name,
            String slug,
            String status,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public static class CreateTenantRequest {
        @NotBlank
        @Size(max = 255)
        private String name;

        @NotBlank
        @Size(max = 100)
        private String slug;

        @Size(max = 50)
        private String status = "ACTIVE";

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getSlug() { return slug; }
        public void setSlug(String slug) { this.slug = slug; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    public static class UpdateTenantRequest {
        @Size(max = 255)
        private String name;

        @Size(max = 100)
        private String slug;

        @Size(max = 50)
        private String status;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getSlug() { return slug; }
        public void setSlug(String slug) { this.slug = slug; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
}

