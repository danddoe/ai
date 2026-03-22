package com.erp.entitybuilder.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "entity_records", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"tenant_id", "entity_id", "external_id"}),
        @UniqueConstraint(columnNames = {"tenant_id", "entity_id", "business_document_number"})
})
public class EntityRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(name = "external_id")
    private String externalId;

    @Column(name = "business_document_number", length = 255)
    private String businessDocumentNumber;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(nullable = false, length = 50)
    private String status = "ACTIVE";

    /** Lowercased, space-separated concatenation of searchable field values (see field {@code config.isSearchable}). */
    @Column(name = "search_vector", nullable = false, length = 8192)
    private String searchVector = "";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getEntityId() { return entityId; }
    public void setEntityId(UUID entityId) { this.entityId = entityId; }

    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }

    public String getBusinessDocumentNumber() { return businessDocumentNumber; }
    public void setBusinessDocumentNumber(String businessDocumentNumber) { this.businessDocumentNumber = businessDocumentNumber; }

    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getSearchVector() { return searchVector; }
    public void setSearchVector(String searchVector) { this.searchVector = searchVector != null ? searchVector : ""; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

