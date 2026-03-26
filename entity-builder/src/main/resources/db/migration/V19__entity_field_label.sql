CREATE TABLE IF NOT EXISTS entity_field_label (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    entity_field_id UUID NOT NULL REFERENCES entity_fields(id) ON DELETE CASCADE,
    locale VARCHAR(16) NOT NULL,
    label VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT entity_field_label_locale_uq UNIQUE (entity_field_id, locale)
);

CREATE INDEX IF NOT EXISTS idx_entity_field_label_tenant ON entity_field_label(tenant_id);
CREATE INDEX IF NOT EXISTS idx_entity_field_label_field ON entity_field_label(entity_field_id);

-- Seed legacy single-locale label_override as English catalog row (idempotent).
INSERT INTO entity_field_label (id, tenant_id, entity_field_id, locale, label, created_at, updated_at)
SELECT gen_random_uuid(), e.tenant_id, ef.id, 'en', trim(both from ef.label_override), now(), now()
FROM entity_fields ef
JOIN entities e ON e.id = ef.entity_id
WHERE ef.label_override IS NOT NULL AND trim(both from ef.label_override) <> ''
ON CONFLICT (entity_field_id, locale) DO NOTHING;
