package com.erp.entitybuilder.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "entity_fields", uniqueConstraints = @UniqueConstraint(columnNames = {"entity_id", "slug"}))
public class EntityField {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, length = 100)
    private String slug;

    @Column(name = "field_type", nullable = false, length = 50)
    private String fieldType;

    @Column(nullable = false)
    private boolean required = false;

    @Column(nullable = false)
    private boolean pii = false;

    @Column(columnDefinition = "jsonb")
    private String config;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Column(name = "label_override", length = 255)
    private String labelOverride;

    @Column(name = "format_string", length = 500)
    private String formatString;

    /** {@link EntityFieldStatuses#ACTIVE} or {@link EntityFieldStatuses#INACTIVE}. */
    @Column(nullable = false, length = 32)
    private String status = EntityFieldStatuses.ACTIVE;

    /** When true, this field's formatted value is a part of the basic record list Display column (ordered by {@link #sortOrder}). */
    @Column(name = "include_in_list_summary_display", nullable = false)
    private boolean includeInListSummaryDisplay = false;

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

    public UUID getEntityId() { return entityId; }
    public void setEntityId(UUID entityId) { this.entityId = entityId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }

    public String getFieldType() { return fieldType; }
    public void setFieldType(String fieldType) { this.fieldType = fieldType; }

    public boolean isRequired() { return required; }
    public void setRequired(boolean required) { this.required = required; }

    public boolean isPii() { return pii; }
    public void setPii(boolean pii) { this.pii = pii; }

    public String getConfig() { return config; }
    public void setConfig(String config) { this.config = config; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

    public String getLabelOverride() { return labelOverride; }
    public void setLabelOverride(String labelOverride) { this.labelOverride = labelOverride; }

    public String getFormatString() { return formatString; }
    public void setFormatString(String formatString) { this.formatString = formatString; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public boolean isIncludeInListSummaryDisplay() { return includeInListSummaryDisplay; }
    public void setIncludeInListSummaryDisplay(boolean includeInListSummaryDisplay) {
        this.includeInListSummaryDisplay = includeInListSummaryDisplay;
    }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

