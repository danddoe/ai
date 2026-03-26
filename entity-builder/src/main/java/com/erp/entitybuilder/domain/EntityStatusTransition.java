package com.erp.entitybuilder.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "entity_status_transition")
public class EntityStatusTransition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "record_scope", nullable = false, length = 32)
    private RecordScope recordScope = RecordScope.TENANT_RECORD;

    @Column(name = "entity_definition_id")
    private UUID entityDefinitionId;

    @Column(name = "from_status_id", nullable = false)
    private UUID fromStatusId;

    @Column(name = "to_status_id", nullable = false)
    private UUID toStatusId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "requires_comment", nullable = false)
    private boolean requiresComment;

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

    public RecordScope getRecordScope() { return recordScope; }
    public void setRecordScope(RecordScope recordScope) { this.recordScope = recordScope; }

    public UUID getEntityDefinitionId() { return entityDefinitionId; }
    public void setEntityDefinitionId(UUID entityDefinitionId) { this.entityDefinitionId = entityDefinitionId; }

    public UUID getFromStatusId() { return fromStatusId; }
    public void setFromStatusId(UUID fromStatusId) { this.fromStatusId = fromStatusId; }

    public UUID getToStatusId() { return toStatusId; }
    public void setToStatusId(UUID toStatusId) { this.toStatusId = toStatusId; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

    public boolean isRequiresComment() { return requiresComment; }
    public void setRequiresComment(boolean requiresComment) { this.requiresComment = requiresComment; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
