-- Atomic counters for document_number generation (per tenant, entity, optional monthly period).
CREATE TABLE IF NOT EXISTS entity_document_number_sequences (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    entity_id UUID NOT NULL REFERENCES entities(id) ON DELETE CASCADE,
    period_key STRING NOT NULL DEFAULT '',
    last_value INT8 NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, entity_id, period_key)
);

CREATE INDEX IF NOT EXISTS idx_entity_doc_num_seq_tenant_entity
    ON entity_document_number_sequences (tenant_id, entity_id);
