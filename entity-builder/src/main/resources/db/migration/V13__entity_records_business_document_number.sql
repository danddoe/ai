-- Human-readable business document number per record (optional). Distinct from external_id (integration keys).
ALTER TABLE entity_records ADD COLUMN IF NOT EXISTS business_document_number VARCHAR(255);

CREATE UNIQUE INDEX IF NOT EXISTS entity_records_tenant_entity_business_doc_uq
    ON entity_records (tenant_id, entity_id, business_document_number);
