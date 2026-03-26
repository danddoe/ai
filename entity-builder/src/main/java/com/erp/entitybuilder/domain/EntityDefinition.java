package com.erp.entitybuilder.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "entities", uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "slug"}))
public class EntityDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 100)
    private String slug;

    @Column(length = 500)
    private String description;

    @Column(name = "default_display_field_slug", length = 100)
    private String defaultDisplayFieldSlug;

    @Column(name = "base_entity_id")
    private UUID baseEntityId;

    /** When set, this row is the synthetic entity for a row in {@code tenant_entity_extensions}. */
    @Column(name = "tenant_entity_extension_id")
    private UUID tenantEntityExtensionId;

    @Column(nullable = false, length = 50)
    private String status = "ACTIVE";

    /** ERP module bucket; vocabulary aligned with IAM portal {@code category_key}. */
    @Column(name = "category_key", length = 64)
    private String categoryKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "definition_scope", nullable = false, length = 32)
    private DefinitionScope definitionScope = DefinitionScope.TENANT_OBJECT;

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
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getDefaultDisplayFieldSlug() { return defaultDisplayFieldSlug; }
    public void setDefaultDisplayFieldSlug(String defaultDisplayFieldSlug) { this.defaultDisplayFieldSlug = defaultDisplayFieldSlug; }

    public UUID getBaseEntityId() { return baseEntityId; }
    public void setBaseEntityId(UUID baseEntityId) { this.baseEntityId = baseEntityId; }

    public UUID getTenantEntityExtensionId() { return tenantEntityExtensionId; }
    public void setTenantEntityExtensionId(UUID tenantEntityExtensionId) { this.tenantEntityExtensionId = tenantEntityExtensionId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCategoryKey() { return categoryKey; }
    public void setCategoryKey(String categoryKey) { this.categoryKey = categoryKey; }

    public DefinitionScope getDefinitionScope() { return definitionScope; }
    public void setDefinitionScope(DefinitionScope definitionScope) { this.definitionScope = definitionScope; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

