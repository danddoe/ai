package com.erp.entitybuilder.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "entity_status")
public class EntityStatus {

    /** Application-assigned (deterministic ids in {@link com.erp.entitybuilder.service.catalog.EntityStatusDynamicEntityProvisioner}). */
    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "record_scope", nullable = false, length = 32)
    private RecordScope recordScope = RecordScope.TENANT_RECORD;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false)
    private String label;

    @Column(length = 2000)
    private String description;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(length = 32)
    private String category;

    @Column(name = "is_initial", nullable = false)
    private boolean initial;

    @Column(name = "is_terminal", nullable = false)
    private boolean terminal;

    @Column(name = "is_open", nullable = false)
    private boolean open = true;

    @Column(name = "blocks_edit", nullable = false)
    private boolean blocksEdit;

    @Column(name = "blocks_delete", nullable = false)
    private boolean blocksDelete;

    @Column(name = "blocks_post", nullable = false)
    private boolean blocksPost;

    @Column(name = "is_default", nullable = false)
    private boolean defaulted;

    @Column(name = "is_system", nullable = false)
    private boolean system;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "valid_from")
    private Instant validFrom;

    @Column(name = "valid_to")
    private Instant validTo;

    @Column(length = 32)
    private String color;

    @Column(name = "icon_key", length = 64)
    private String iconKey;

    @Column(columnDefinition = "jsonb")
    private String metadata;

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

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public boolean isInitial() { return initial; }
    public void setInitial(boolean initial) { this.initial = initial; }

    public boolean isTerminal() { return terminal; }
    public void setTerminal(boolean terminal) { this.terminal = terminal; }

    public boolean isOpen() { return open; }
    public void setOpen(boolean open) { this.open = open; }

    public boolean isBlocksEdit() { return blocksEdit; }
    public void setBlocksEdit(boolean blocksEdit) { this.blocksEdit = blocksEdit; }

    public boolean isBlocksDelete() { return blocksDelete; }
    public void setBlocksDelete(boolean blocksDelete) { this.blocksDelete = blocksDelete; }

    public boolean isBlocksPost() { return blocksPost; }
    public void setBlocksPost(boolean blocksPost) { this.blocksPost = blocksPost; }

    public boolean isDefaulted() { return defaulted; }
    public void setDefaulted(boolean defaulted) { this.defaulted = defaulted; }

    public boolean isSystem() { return system; }
    public void setSystem(boolean system) { this.system = system; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Instant getValidFrom() { return validFrom; }
    public void setValidFrom(Instant validFrom) { this.validFrom = validFrom; }

    public Instant getValidTo() { return validTo; }
    public void setValidTo(Instant validTo) { this.validTo = validTo; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public String getIconKey() { return iconKey; }
    public void setIconKey(String iconKey) { this.iconKey = iconKey; }

    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
