package com.erp.coreservice.domain;

import com.erp.audit.AuditableResource;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "business_units")
@AuditableResource(path = "core.business_unit")
public class BusinessUnit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "bu_id")
    private UUID buId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "parent_bu_id")
    private UUID parentBuId;

    @Column(name = "bu_type", nullable = false, length = 100)
    private String buType;

    @Column(name = "bu_name", nullable = false)
    private String buName;

    @Column(name = "slug", length = 128)
    private String slug;

    @Column(name = "alias", length = 255)
    private String alias;

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

    public UUID getBuId() {
        return buId;
    }

    public void setBuId(UUID buId) {
        this.buId = buId;
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

    public UUID getParentBuId() {
        return parentBuId;
    }

    public void setParentBuId(UUID parentBuId) {
        this.parentBuId = parentBuId;
    }

    public String getBuType() {
        return buType;
    }

    public void setBuType(String buType) {
        this.buType = buType;
    }

    public String getBuName() {
        return buName;
    }

    public void setBuName(String buName) {
        this.buName = buName;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
