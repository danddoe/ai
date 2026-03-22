package com.erp.entitybuilder.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "form_layouts", uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "entity_id", "name"}))
public class FormLayout {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "jsonb", nullable = false)
    private String layout;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault = false;

    /** DB-maintained; enforces at most one default per (tenant, entity) on Cockroach. Not written by JPA. */
    @Column(name = "default_uniqueness_slot", insertable = false, updatable = false)
    private Long defaultUniquenessSlot;

    @Column(nullable = false, length = 50)
    private String status = "ACTIVE";

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

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getLayout() { return layout; }
    public void setLayout(String layout) { this.layout = layout; }

    public boolean isDefault() { return isDefault; }
    public void setDefault(boolean aDefault) { isDefault = aDefault; }

    public Long getDefaultUniquenessSlot() { return defaultUniquenessSlot; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

