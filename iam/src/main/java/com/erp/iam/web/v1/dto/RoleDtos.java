package com.erp.iam.web.v1.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public class RoleDtos {

    public record RoleDto(
            UUID id,
            UUID tenantId,
            String name,
            String description,
            boolean system,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public static class CreateRoleRequest {
        @NotBlank
        @Size(max = 100)
        private String name;

        @Size(max = 500)
        private String description;

        private boolean system = false;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public boolean isSystem() { return system; }
        public void setSystem(boolean system) { this.system = system; }
    }

    public static class UpdateRoleRequest {
        @Size(max = 100)
        private String name;

        @Size(max = 500)
        private String description;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }
}

