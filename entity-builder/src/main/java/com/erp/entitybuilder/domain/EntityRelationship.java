package com.erp.entitybuilder.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "entity_relationships", indexes = {
        @Index(name = "idx_entity_relationships_tenant_id", columnList = "tenant_id")
})
public class EntityRelationship {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, length = 100)
    private String slug;

    @Column(name = "from_entity_id", nullable = false)
    private UUID fromEntityId;

    @Column(name = "to_entity_id", nullable = false)
    private UUID toEntityId;

    @Column(name = "from_field_slug", length = 100)
    private String fromFieldSlug;

    @Column(name = "to_field_slug", length = 100)
    private String toFieldSlug;

    @Column(nullable = false, length = 50)
    private String cardinality;

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

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }

    public UUID getFromEntityId() { return fromEntityId; }
    public void setFromEntityId(UUID fromEntityId) { this.fromEntityId = fromEntityId; }

    public UUID getToEntityId() { return toEntityId; }
    public void setToEntityId(UUID toEntityId) { this.toEntityId = toEntityId; }

    public String getFromFieldSlug() { return fromFieldSlug; }
    public void setFromFieldSlug(String fromFieldSlug) { this.fromFieldSlug = fromFieldSlug; }

    public String getToFieldSlug() { return toFieldSlug; }
    public void setToFieldSlug(String toFieldSlug) { this.toFieldSlug = toFieldSlug; }

    public String getCardinality() { return cardinality; }
    public void setCardinality(String cardinality) { this.cardinality = cardinality; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

