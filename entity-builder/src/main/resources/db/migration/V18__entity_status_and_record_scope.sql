-- ERP-style entity status master, transitions, labels, record scope on entity_records.

CREATE TABLE IF NOT EXISTS entity_status (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    record_scope VARCHAR(32) NOT NULL DEFAULT 'TENANT_RECORD',
    entity_definition_id UUID REFERENCES entities(id) ON DELETE CASCADE,
    code VARCHAR(64) NOT NULL,
    label VARCHAR(255) NOT NULL,
    description VARCHAR(2000),
    sort_order INT NOT NULL DEFAULT 0,
    category VARCHAR(32),
    is_initial BOOLEAN NOT NULL DEFAULT false,
    is_terminal BOOLEAN NOT NULL DEFAULT false,
    is_open BOOLEAN NOT NULL DEFAULT true,
    blocks_edit BOOLEAN NOT NULL DEFAULT false,
    blocks_delete BOOLEAN NOT NULL DEFAULT false,
    blocks_post BOOLEAN NOT NULL DEFAULT false,
    is_default BOOLEAN NOT NULL DEFAULT false,
    is_system BOOLEAN NOT NULL DEFAULT false,
    is_active BOOLEAN NOT NULL DEFAULT true,
    valid_from TIMESTAMPTZ,
    valid_to TIMESTAMPTZ,
    color VARCHAR(32),
    icon_key VARCHAR(64),
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT entity_status_record_scope_chk CHECK (record_scope IN ('STANDARD_RECORD', 'TENANT_RECORD'))
);

CREATE UNIQUE INDEX IF NOT EXISTS entity_status_tenant_def_code_uq
    ON entity_status (tenant_id, entity_definition_id, code)
    WHERE entity_definition_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS entity_status_tenant_null_def_code_uq
    ON entity_status (tenant_id, code)
    WHERE entity_definition_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_entity_status_tenant_id ON entity_status(tenant_id);
CREATE INDEX IF NOT EXISTS idx_entity_status_entity_definition_id ON entity_status(entity_definition_id);

CREATE TABLE IF NOT EXISTS entity_status_label (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    record_scope VARCHAR(32) NOT NULL DEFAULT 'TENANT_RECORD',
    entity_status_id UUID NOT NULL REFERENCES entity_status(id) ON DELETE CASCADE,
    locale VARCHAR(16) NOT NULL,
    label VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT entity_status_label_scope_chk CHECK (record_scope IN ('STANDARD_RECORD', 'TENANT_RECORD')),
    CONSTRAINT entity_status_label_locale_uq UNIQUE (entity_status_id, locale)
);

CREATE INDEX IF NOT EXISTS idx_entity_status_label_tenant ON entity_status_label(tenant_id);

CREATE TABLE IF NOT EXISTS entity_status_transition (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    record_scope VARCHAR(32) NOT NULL DEFAULT 'TENANT_RECORD',
    entity_definition_id UUID REFERENCES entities(id) ON DELETE CASCADE,
    from_status_id UUID NOT NULL REFERENCES entity_status(id) ON DELETE CASCADE,
    to_status_id UUID NOT NULL REFERENCES entity_status(id) ON DELETE CASCADE,
    sort_order INT NOT NULL DEFAULT 0,
    requires_comment BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT entity_status_transition_scope_chk CHECK (record_scope IN ('STANDARD_RECORD', 'TENANT_RECORD')),
    CONSTRAINT entity_status_transition_uq UNIQUE (tenant_id, from_status_id, to_status_id)
);

CREATE INDEX IF NOT EXISTS idx_entity_status_transition_tenant ON entity_status_transition(tenant_id);
CREATE INDEX IF NOT EXISTS idx_entity_status_transition_from ON entity_status_transition(from_status_id);

ALTER TABLE entity_records ADD COLUMN IF NOT EXISTS record_scope VARCHAR(32) NOT NULL DEFAULT 'TENANT_RECORD';
ALTER TABLE entity_records ADD CONSTRAINT entity_records_record_scope_chk CHECK (record_scope IN ('STANDARD_RECORD', 'TENANT_RECORD'));

ALTER TABLE entity_records ADD COLUMN IF NOT EXISTS entity_status_id UUID REFERENCES entity_status(id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS idx_entity_records_entity_status_id ON entity_records(tenant_id, entity_status_id);
