-- Core schema for dynamic entity builder

CREATE TABLE IF NOT EXISTS entities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    base_entity_id UUID,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(tenant_id, slug)
);

CREATE INDEX IF NOT EXISTS idx_entities_tenant_id ON entities(tenant_id);

CREATE TABLE IF NOT EXISTS entity_fields (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_id UUID NOT NULL REFERENCES entities(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(100) NOT NULL,
    field_type VARCHAR(50) NOT NULL, -- string, number, date, boolean, reference
    required BOOLEAN NOT NULL DEFAULT false,
    pii BOOLEAN NOT NULL DEFAULT false,
    config JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(entity_id, slug)
);

CREATE INDEX IF NOT EXISTS idx_entity_fields_entity_id ON entity_fields(entity_id);

CREATE TABLE IF NOT EXISTS entity_relationships (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    from_entity_id UUID NOT NULL REFERENCES entities(id) ON DELETE CASCADE,
    to_entity_id UUID NOT NULL REFERENCES entities(id) ON DELETE CASCADE,
    from_field_slug VARCHAR(100),
    to_field_slug VARCHAR(100),
    cardinality VARCHAR(50) NOT NULL, -- one-to-one, one-to-many, many-to-many
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_entity_relationships_tenant_id ON entity_relationships(tenant_id);
CREATE INDEX IF NOT EXISTS idx_entity_relationships_from_entity_id ON entity_relationships(from_entity_id);
CREATE INDEX IF NOT EXISTS idx_entity_relationships_to_entity_id ON entity_relationships(to_entity_id);

CREATE TABLE IF NOT EXISTS tenant_entity_extensions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    base_entity_id UUID NOT NULL REFERENCES entities(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(tenant_id, slug)
);

CREATE INDEX IF NOT EXISTS idx_tenant_entity_extensions_tenant_id ON tenant_entity_extensions(tenant_id);

CREATE TABLE IF NOT EXISTS tenant_entity_extension_fields (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_entity_extension_id UUID NOT NULL REFERENCES tenant_entity_extensions(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(100) NOT NULL,
    field_type VARCHAR(50) NOT NULL,
    required BOOLEAN NOT NULL DEFAULT false,
    pii BOOLEAN NOT NULL DEFAULT false,
    config JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(tenant_entity_extension_id, slug)
);

CREATE TABLE IF NOT EXISTS entity_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    entity_id UUID NOT NULL REFERENCES entities(id) ON DELETE CASCADE,
    external_id VARCHAR(255),
    created_by UUID,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(tenant_id, entity_id, external_id)
);

CREATE INDEX IF NOT EXISTS idx_entity_records_tenant_id ON entity_records(tenant_id);
CREATE INDEX IF NOT EXISTS idx_entity_records_entity_id ON entity_records(entity_id);

CREATE TABLE IF NOT EXISTS entity_record_values (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    record_id UUID NOT NULL REFERENCES entity_records(id) ON DELETE CASCADE,
    field_id UUID NOT NULL REFERENCES entity_fields(id) ON DELETE CASCADE,
    value_text VARCHAR(1000),
    value_number DECIMAL(38,10),
    value_date TIMESTAMPTZ,
    value_boolean BOOLEAN,
    value_reference UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(record_id, field_id)
);

CREATE INDEX IF NOT EXISTS idx_entity_record_values_record_id ON entity_record_values(record_id);
CREATE INDEX IF NOT EXISTS idx_entity_record_values_field_id ON entity_record_values(field_id);

CREATE TABLE IF NOT EXISTS record_links (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    from_record_id UUID NOT NULL REFERENCES entity_records(id) ON DELETE CASCADE,
    to_record_id UUID NOT NULL REFERENCES entity_records(id) ON DELETE CASCADE,
    relationship_id UUID NOT NULL REFERENCES entity_relationships(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(tenant_id, from_record_id, to_record_id, relationship_id)
);

CREATE INDEX IF NOT EXISTS idx_record_links_tenant_id ON record_links(tenant_id);

CREATE TABLE IF NOT EXISTS pii_vault (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    record_id UUID NOT NULL REFERENCES entity_records(id) ON DELETE CASCADE,
    field_id UUID NOT NULL REFERENCES entity_fields(id) ON DELETE CASCADE,
    encrypted_value VARCHAR(2000) NOT NULL,
    key_id VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(tenant_id, record_id, field_id)
);

CREATE INDEX IF NOT EXISTS idx_pii_vault_tenant_id ON pii_vault(tenant_id);

CREATE TABLE IF NOT EXISTS form_layouts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    entity_id UUID NOT NULL REFERENCES entities(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    layout JSONB NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT false,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(tenant_id, entity_id, name)
);

CREATE INDEX IF NOT EXISTS idx_form_layouts_tenant_id ON form_layouts(tenant_id);

-- Idempotency entries for record creation
CREATE TABLE IF NOT EXISTS idempotency_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    user_id UUID NOT NULL,
    method VARCHAR(10) NOT NULL,
    route_template VARCHAR(255) NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    status_code INTEGER NOT NULL,
    response_json JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    UNIQUE(tenant_id, user_id, method, route_template, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_idempotency_requests_expires_at ON idempotency_requests(expires_at);

