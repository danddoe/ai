package com.erp.iam.web.v1.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public class UserDtos {

    public record UserDto(
            UUID id,
            String email,
            String displayName,
            String status,
            Instant emailVerifiedAt,
            Instant lastLoginAt,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public static class CreateUserRequest {
        @NotBlank
        @Email
        @Size(max = 255)
        private String email;

        @Size(max = 255)
        private String displayName;

        @NotBlank
        @Size(min = 8, max = 200)
        private String password;

        @Size(max = 50)
        private String status = "ACTIVE";

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    public static class UpdateUserRequest {
        @Email
        @Size(max = 255)
        private String email;

        @Size(max = 255)
        private String displayName;

        @Size(max = 50)
        private String status;

        @Size(min = 8, max = 200)
        private String password;

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }
}

