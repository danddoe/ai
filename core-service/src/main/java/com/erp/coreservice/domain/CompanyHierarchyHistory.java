package com.erp.coreservice.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "company_hierarchy_history")
public class CompanyHierarchyHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "hierarchy_id")
    private UUID hierarchyId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "parent_company_id")
    private UUID parentCompanyId;

    @Column(name = "child_company_id", nullable = false)
    private UUID childCompanyId;

    @Column(name = "ownership_pct", precision = 5, scale = 2)
    private BigDecimal ownershipPct;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public UUID getHierarchyId() {
        return hierarchyId;
    }

    public void setHierarchyId(UUID hierarchyId) {
        this.hierarchyId = hierarchyId;
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

    public UUID getChildCompanyId() {
        return childCompanyId;
    }

    public void setChildCompanyId(UUID childCompanyId) {
        this.childCompanyId = childCompanyId;
    }

    public BigDecimal getOwnershipPct() {
        return ownershipPct;
    }

    public void setOwnershipPct(BigDecimal ownershipPct) {
        this.ownershipPct = ownershipPct;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
