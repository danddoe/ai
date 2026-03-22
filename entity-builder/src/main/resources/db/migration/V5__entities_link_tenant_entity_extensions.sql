-- Link synthetic extension rows in `entities` to authoritative `tenant_entity_extensions` metadata.
-- ON DELETE CASCADE: deleting a tenant_entity_extensions row removes the linked entities row (and cascades to fields, records, etc.).

ALTER TABLE entities ADD COLUMN IF NOT EXISTS tenant_entity_extension_id UUID NULL;

-- One entities row per extension metadata row (nullable for normal entities)
CREATE UNIQUE INDEX IF NOT EXISTS uq_entities_tenant_entity_extension_id
    ON entities (tenant_entity_extension_id)
    WHERE tenant_entity_extension_id IS NOT NULL;

-- Backfill metadata rows from existing extension entities (base_entity_id IS NOT NULL)
INSERT INTO tenant_entity_extensions (id, tenant_id, base_entity_id, name, slug, status, created_at, updated_at)
SELECT gen_random_uuid(), e.tenant_id, e.base_entity_id, e.name, e.slug, e.status, e.created_at, e.updated_at
FROM entities e
WHERE e.base_entity_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM tenant_entity_extensions te
      WHERE te.tenant_id = e.tenant_id
        AND te.base_entity_id = e.base_entity_id
        AND te.slug = e.slug
  );

UPDATE entities AS e
SET tenant_entity_extension_id = te.id
FROM tenant_entity_extensions AS te
WHERE e.base_entity_id IS NOT NULL
  AND e.tenant_entity_extension_id IS NULL
  AND e.tenant_id = te.tenant_id
  AND e.slug = te.slug
  AND e.base_entity_id = te.base_entity_id;

INSERT INTO tenant_entity_extension_fields (
    id, tenant_entity_extension_id, name, slug, field_type, required, pii, config, created_at, updated_at
)
SELECT gen_random_uuid(),
       e.tenant_entity_extension_id,
       ef.name,
       ef.slug,
       ef.field_type,
       ef.required,
       ef.pii,
       ef.config,
       ef.created_at,
       ef.updated_at
FROM entity_fields ef
JOIN entities e ON ef.entity_id = e.id
WHERE e.tenant_entity_extension_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM tenant_entity_extension_fields tef
      WHERE tef.tenant_entity_extension_id = e.tenant_entity_extension_id
        AND tef.slug = ef.slug
  );

ALTER TABLE entities
    ADD CONSTRAINT fk_entities_tenant_entity_extension
    FOREIGN KEY (tenant_entity_extension_id) REFERENCES tenant_entity_extensions (id) ON DELETE CASCADE;
