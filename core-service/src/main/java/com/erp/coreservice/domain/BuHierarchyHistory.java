package com.erp.coreservice.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "bu_hierarchy_history")
public class BuHierarchyHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "hierarchy_id")
    private UUID hierarchyId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "parent_bu_id")
    private UUID parentBuId;

    @Column(name = "child_bu_id", nullable = false)
    private UUID childBuId;

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

    public UUID getParentBuId() {
        return parentBuId;
    }

    public void setParentBuId(UUID parentBuId) {
        this.parentBuId = parentBuId;
    }

    public UUID getChildBuId() {
        return childBuId;
    }

    public void setChildBuId(UUID childBuId) {
        this.childBuId = childBuId;
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
