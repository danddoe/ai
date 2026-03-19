package com.erp.iam.web.v1.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public class TenantMemberDtos {

    public record TenantMemberDto(
            UUID tenantId,
            UUID userId,
            String email,
            String userDisplayName,
            String tenantDisplayName,
            String status,
            Instant invitedAt,
            Instant joinedAt,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public static class AddMemberRequest {
        private UUID userId;

        @Email
        @Size(max = 255)
        private String email;

        @Size(max = 255)
        private String displayName;

        @Size(max = 50)
        private String status = "ACTIVE";

        public UUID getUserId() { return userId; }
        public void setUserId(UUID userId) { this.userId = userId; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    public static class UpdateMemberRequest {
        @Size(max = 255)
        private String displayName;

        @Size(max = 50)
        private String status;

        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
}

