package com.erp.entitybuilder.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "record_links", uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "from_record_id", "to_record_id", "relationship_id"}))
public class RecordLink {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "from_record_id", nullable = false)
    private UUID fromRecordId;

    @Column(name = "to_record_id", nullable = false)
    private UUID toRecordId;

    @Column(name = "relationship_id", nullable = false)
    private UUID relationshipId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getFromRecordId() { return fromRecordId; }
    public void setFromRecordId(UUID fromRecordId) { this.fromRecordId = fromRecordId; }

    public UUID getToRecordId() { return toRecordId; }
    public void setToRecordId(UUID toRecordId) { this.toRecordId = toRecordId; }

    public UUID getRelationshipId() { return relationshipId; }
    public void setRelationshipId(UUID relationshipId) { this.relationshipId = relationshipId; }

    public Instant getCreatedAt() { return createdAt; }
}

