package com.erp.coreservice.web.v1.dto;

import com.erp.coreservice.domain.PortalHostBindingScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public class PortalDtos {

    public record PortalBootstrapResponse(
            UUID tenantId,
            UUID companyId,
            UUID defaultBuId,
            PortalHostBindingScope scope
    ) {
    }

    public record PortalHostBindingDto(
            UUID bindingId,
            String hostname,
            PortalHostBindingScope scope,
            UUID tenantId,
            UUID companyId,
            UUID buId,
            Instant verifiedAt,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public static class CreatePortalHostBindingRequest {
        @NotBlank
        @Size(max = 253)
        private String hostname;

        @NotNull
        private PortalHostBindingScope scope;

        private UUID companyId;

        private UUID buId;

        private Instant verifiedAt;

        public String getHostname() {
            return hostname;
        }

        public void setHostname(String hostname) {
            this.hostname = hostname;
        }

        public PortalHostBindingScope getScope() {
            return scope;
        }

        public void setScope(PortalHostBindingScope scope) {
            this.scope = scope;
        }

        public UUID getCompanyId() {
            return companyId;
        }

        public void setCompanyId(UUID companyId) {
            this.companyId = companyId;
        }

        public UUID getBuId() {
            return buId;
        }

        public void setBuId(UUID buId) {
            this.buId = buId;
        }

        public Instant getVerifiedAt() {
            return verifiedAt;
        }

        public void setVerifiedAt(Instant verifiedAt) {
            this.verifiedAt = verifiedAt;
        }
    }

    public static class PatchPortalHostBindingRequest {
        @Size(max = 253)
        private String hostname;

        private PortalHostBindingScope scope;

        private UUID companyId;

        private UUID buId;

        private Instant verifiedAt;

        private boolean clearVerifiedAt;

        public String getHostname() {
            return hostname;
        }

        public void setHostname(String hostname) {
            this.hostname = hostname;
        }

        public PortalHostBindingScope getScope() {
            return scope;
        }

        public void setScope(PortalHostBindingScope scope) {
            this.scope = scope;
        }

        public UUID getCompanyId() {
            return companyId;
        }

        public void setCompanyId(UUID companyId) {
            this.companyId = companyId;
        }

        public UUID getBuId() {
            return buId;
        }

        public void setBuId(UUID buId) {
            this.buId = buId;
        }

        public Instant getVerifiedAt() {
            return verifiedAt;
        }

        public void setVerifiedAt(Instant verifiedAt) {
            this.verifiedAt = verifiedAt;
        }

        public boolean isClearVerifiedAt() {
            return clearVerifiedAt;
        }

        public void setClearVerifiedAt(boolean clearVerifiedAt) {
            this.clearVerifiedAt = clearVerifiedAt;
        }
    }
}
