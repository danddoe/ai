package com.erp.entitybuilder.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "global_search_documents",
        uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "source_type", "source_record_id"}))
public class GlobalSearchDocument {

    public static final String SOURCE_ENTITY_RECORD = "entity_record";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "source_type", nullable = false, length = 64)
    private String sourceType;

    @Column(name = "source_entity_id", nullable = false)
    private UUID sourceEntityId;

    @Column(name = "source_record_id", nullable = false)
    private UUID sourceRecordId;

    @Column(nullable = false, length = 512)
    private String title;

    @Column(nullable = false, length = 1000)
    private String subtitle = "";

    @Column(name = "route_path", nullable = false, length = 512)
    private String routePath;

    @Column(name = "search_text", nullable = false, length = 8192)
    private String searchText;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public UUID getSourceEntityId() {
        return sourceEntityId;
    }

    public void setSourceEntityId(UUID sourceEntityId) {
        this.sourceEntityId = sourceEntityId;
    }

    public UUID getSourceRecordId() {
        return sourceRecordId;
    }

    public void setSourceRecordId(UUID sourceRecordId) {
        this.sourceRecordId = sourceRecordId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public void setSubtitle(String subtitle) {
        this.subtitle = subtitle != null ? subtitle : "";
    }

    public String getRoutePath() {
        return routePath;
    }

    public void setRoutePath(String routePath) {
        this.routePath = routePath;
    }

    public String getSearchText() {
        return searchText;
    }

    public void setSearchText(String searchText) {
        this.searchText = searchText;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
