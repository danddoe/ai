package com.erp.entitybuilder.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "business_rule",
        uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "entity_id", "name"})
)
public class BusinessRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(name = "form_layout_id")
    private UUID formLayoutId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private int priority;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_name", nullable = false, length = 64)
    private BusinessRuleTrigger trigger = BusinessRuleTrigger.ON_FORM_CHANGE;

    @Column(name = "condition_json", columnDefinition = "jsonb", nullable = false)
    private String conditionJson;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @OneToMany(mappedBy = "businessRule", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("priority ASC, id ASC")
    private List<BusinessRuleAction> actions = new ArrayList<>();

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

    public void addAction(BusinessRuleAction a) {
        actions.add(a);
        a.setBusinessRule(this);
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getEntityId() { return entityId; }
    public void setEntityId(UUID entityId) { this.entityId = entityId; }
    public UUID getFormLayoutId() { return formLayoutId; }
    public void setFormLayoutId(UUID formLayoutId) { this.formLayoutId = formLayoutId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
    public BusinessRuleTrigger getTrigger() { return trigger; }
    public void setTrigger(BusinessRuleTrigger trigger) { this.trigger = trigger; }
    public String getConditionJson() { return conditionJson; }
    public void setConditionJson(String conditionJson) { this.conditionJson = conditionJson; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public List<BusinessRuleAction> getActions() { return actions; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
