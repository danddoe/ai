package com.erp.iam.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "portal_navigation_items")
public class PortalNavigationItem {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "parent_id")
    private UUID parentId;

    /** When set, item is visible only to users of this tenant (in addition to required_permissions / roles). Null = global. */
    @Column(name = "tenant_id", columnDefinition = "uuid")
    private UUID tenantId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "route_path", length = 2048)
    private String routePath;

    @Column(nullable = false, length = 255)
    private String label;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false, length = 32)
    private String type = "internal";

    @Column(length = 128)
    private String icon;

    /** Stable key for module / product area (e.g. entity_builder, accounting, accounts_payable). Used for grouped nav; hierarchy is still parent_id. */
    @Column(name = "category_key", length = 64)
    private String categoryKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "search_keywords", columnDefinition = "jsonb", nullable = false)
    private List<String> searchKeywords = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "required_permissions", columnDefinition = "jsonb", nullable = false)
    private List<String> requiredPermissions = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "required_roles", columnDefinition = "jsonb", nullable = false)
    private List<String> requiredRoles = new ArrayList<>();

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    /** PUBLISHED (live) or WIP (being edited in designers). */
    @Column(name = "design_status", nullable = false, length = 32)
    private String designStatus = "PUBLISHED";

    /** Optional entity-builder record_list_views.id (same tenant; not enforced as FK). */
    @Column(name = "linked_list_view_id", columnDefinition = "uuid")
    private UUID linkedListViewId;

    /** Optional entity-builder form_layouts.id (same tenant; not enforced as FK). */
    @Column(name = "linked_form_layout_id", columnDefinition = "uuid")
    private UUID linkedFormLayoutId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getParentId() {
        return parentId;
    }

    public void setParentId(UUID parentId) {
        this.parentId = parentId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getRoutePath() {
        return routePath;
    }

    public void setRoutePath(String routePath) {
        this.routePath = routePath;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public String getCategoryKey() {
        return categoryKey;
    }

    public void setCategoryKey(String categoryKey) {
        this.categoryKey = categoryKey;
    }

    public List<String> getSearchKeywords() {
        return searchKeywords != null ? searchKeywords : List.of();
    }

    public void setSearchKeywords(List<String> searchKeywords) {
        this.searchKeywords = searchKeywords != null ? searchKeywords : new ArrayList<>();
    }

    public List<String> getRequiredPermissions() {
        return requiredPermissions != null ? requiredPermissions : List.of();
    }

    public void setRequiredPermissions(List<String> requiredPermissions) {
        this.requiredPermissions = requiredPermissions != null ? requiredPermissions : new ArrayList<>();
    }

    public List<String> getRequiredRoles() {
        return requiredRoles != null ? requiredRoles : List.of();
    }

    public void setRequiredRoles(List<String> requiredRoles) {
        this.requiredRoles = requiredRoles != null ? requiredRoles : new ArrayList<>();
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getDesignStatus() {
        return designStatus;
    }

    public void setDesignStatus(String designStatus) {
        this.designStatus = designStatus != null ? designStatus : "PUBLISHED";
    }

    public UUID getLinkedListViewId() {
        return linkedListViewId;
    }

    public void setLinkedListViewId(UUID linkedListViewId) {
        this.linkedListViewId = linkedListViewId;
    }

    public UUID getLinkedFormLayoutId() {
        return linkedFormLayoutId;
    }

    public void setLinkedFormLayoutId(UUID linkedFormLayoutId) {
        this.linkedFormLayoutId = linkedFormLayoutId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
