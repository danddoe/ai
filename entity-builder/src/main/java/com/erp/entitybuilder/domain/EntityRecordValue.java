package com.erp.entitybuilder.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "entity_record_values", uniqueConstraints = @UniqueConstraint(columnNames = {"record_id", "field_id"}))
public class EntityRecordValue {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "record_id", nullable = false)
    private UUID recordId;

    @Column(name = "field_id", nullable = false)
    private UUID fieldId;

    @Column(name = "value_text")
    private String valueText;

    @Column(name = "value_number")
    private BigDecimal valueNumber;

    @Column(name = "value_date")
    private Instant valueDate;

    @Column(name = "value_boolean")
    private Boolean valueBoolean;

    @Column(name = "value_reference")
    private UUID valueReference;

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

    public UUID getRecordId() { return recordId; }
    public void setRecordId(UUID recordId) { this.recordId = recordId; }

    public UUID getFieldId() { return fieldId; }
    public void setFieldId(UUID fieldId) { this.fieldId = fieldId; }

    public String getValueText() { return valueText; }
    public void setValueText(String valueText) { this.valueText = valueText; }

    public BigDecimal getValueNumber() { return valueNumber; }
    public void setValueNumber(BigDecimal valueNumber) { this.valueNumber = valueNumber; }

    public Instant getValueDate() { return valueDate; }
    public void setValueDate(Instant valueDate) { this.valueDate = valueDate; }

    public Boolean getValueBoolean() { return valueBoolean; }
    public void setValueBoolean(Boolean valueBoolean) { this.valueBoolean = valueBoolean; }

    public UUID getValueReference() { return valueReference; }
    public void setValueReference(UUID valueReference) { this.valueReference = valueReference; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

