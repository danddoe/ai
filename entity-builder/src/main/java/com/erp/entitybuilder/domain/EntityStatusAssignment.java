package com.erp.entitybuilder.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "entity_status_assignment",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"tenant_id", "assignment_scope", "scope_id", "entity_status_id"}
        )
)
public class EntityStatusAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "assignment_scope", nullable = false, length = 32)
    private AssignmentScope assignmentScope;

    @Column(name = "scope_id", nullable = false)
    private UUID scopeId;

    @Column(name = "entity_status_id", nullable = false)
    private UUID entityStatusId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant n = Instant.now();
        createdAt = n;
        updatedAt = n;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public AssignmentScope getAssignmentScope() { return assignmentScope; }
    public void setAssignmentScope(AssignmentScope assignmentScope) { this.assignmentScope = assignmentScope; }

    public UUID getScopeId() { return scopeId; }
    public void setScopeId(UUID scopeId) { this.scopeId = scopeId; }

    public UUID getEntityStatusId() { return entityStatusId; }
    public void setEntityStatusId(UUID entityStatusId) { this.entityStatusId = entityStatusId; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
