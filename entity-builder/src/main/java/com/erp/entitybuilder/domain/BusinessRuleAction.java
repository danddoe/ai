package com.erp.entitybuilder.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "business_rule_action")
public class BusinessRuleAction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "business_rule_id", nullable = false)
    private BusinessRule businessRule;

    @Column(nullable = false)
    private int priority;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 64)
    private BusinessRuleActionType actionType;

    @Column(columnDefinition = "jsonb", nullable = false)
    private String payload;

    @Column(name = "apply_ui", nullable = false)
    private boolean applyUi;

    @Column(name = "apply_server", nullable = false)
    private boolean applyServer;

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
    public BusinessRule getBusinessRule() { return businessRule; }
    public void setBusinessRule(BusinessRule businessRule) { this.businessRule = businessRule; }
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
    public BusinessRuleActionType getActionType() { return actionType; }
    public void setActionType(BusinessRuleActionType actionType) { this.actionType = actionType; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public boolean isApplyUi() { return applyUi; }
    public void setApplyUi(boolean applyUi) { this.applyUi = applyUi; }
    public boolean isApplyServer() { return applyServer; }
    public void setApplyServer(boolean applyServer) { this.applyServer = applyServer; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
