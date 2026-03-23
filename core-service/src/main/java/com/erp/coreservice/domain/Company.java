package com.erp.coreservice.domain;

import com.erp.audit.AuditableResource;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "companies")
@AuditableResource(path = "core.company")
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "company_id")
    private UUID companyId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "parent_company_id")
    private UUID parentCompanyId;

    @Column(name = "company_name", nullable = false)
    private String companyName;

    @Column(name = "slug", length = 128)
    private String slug;

    @Column(name = "alias", length = 255)
    private String alias;

    @Column(name = "ownership_pct", precision = 5, scale = 2)
    private BigDecimal ownershipPct;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "base_currency", nullable = false, length = 3)
    private String baseCurrency;

    @Column(name = "default_portal_bu_id")
    private UUID defaultPortalBuId;

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

    public UUID getCompanyId() {
        return companyId;
    }

    public void setCompanyId(UUID companyId) {
        this.companyId = companyId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getParentCompanyId() {
        return parentCompanyId;
    }

    public void setParentCompanyId(UUID parentCompanyId) {
        this.parentCompanyId = parentCompanyId;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
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

    public BigDecimal getOwnershipPct() {
        return ownershipPct;
    }

    public void setOwnershipPct(BigDecimal ownershipPct) {
        this.ownershipPct = ownershipPct;
    }

    public String getBaseCurrency() {
        return baseCurrency;
    }

    public void setBaseCurrency(String baseCurrency) {
        this.baseCurrency = baseCurrency;
    }

    public UUID getDefaultPortalBuId() {
        return defaultPortalBuId;
    }

    public void setDefaultPortalBuId(UUID defaultPortalBuId) {
        this.defaultPortalBuId = defaultPortalBuId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
