-- Declarative business rules (UI + server surfaces) per entity.

CREATE TABLE business_rule (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    entity_id UUID NOT NULL REFERENCES entities(id) ON DELETE CASCADE,
    form_layout_id UUID REFERENCES form_layouts(id) ON DELETE SET NULL,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(500),
    priority INT NOT NULL DEFAULT 0,
    trigger_name VARCHAR(64) NOT NULL,
    condition_json JSONB NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, entity_id, name)
);

CREATE INDEX idx_business_rule_tenant_entity_trigger
    ON business_rule (tenant_id, entity_id, trigger_name, is_active);

CREATE INDEX idx_business_rule_tenant_layout
    ON business_rule (tenant_id, form_layout_id);

CREATE TABLE business_rule_action (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_rule_id UUID NOT NULL REFERENCES business_rule(id) ON DELETE CASCADE,
    priority INT NOT NULL DEFAULT 0,
    action_type VARCHAR(64) NOT NULL,
    payload JSONB NOT NULL,
    apply_ui BOOLEAN NOT NULL DEFAULT false,
    apply_server BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_business_rule_action_rule ON business_rule_action (business_rule_id);
