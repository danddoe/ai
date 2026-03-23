package com.erp.coreservice.domain;

import com.erp.audit.AuditableResource;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "portal_host_bindings")
@AuditableResource(path = "core.portal_host_binding")
public class PortalHostBinding {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "binding_id")
    private UUID bindingId;

    @Column(name = "hostname", nullable = false, length = 253)
    private String hostname;

    @Column(name = "slug", length = 128)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 32)
    private PortalHostBindingScope scope;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "company_id")
    private UUID companyId;

    @Column(name = "bu_id")
    private UUID buId;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getBindingId() {
        return bindingId;
    }

    public void setBindingId(UUID bindingId) {
        this.bindingId = bindingId;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public PortalHostBindingScope getScope() {
        return scope;
    }

    public void setScope(PortalHostBindingScope scope) {
        this.scope = scope;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
