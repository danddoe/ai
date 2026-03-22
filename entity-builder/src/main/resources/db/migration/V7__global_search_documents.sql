-- Flattened cross-entity index for omnibox / global record search (one row per dynamic record).
CREATE TABLE IF NOT EXISTS global_search_documents (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    source_type STRING NOT NULL,
    source_entity_id UUID NOT NULL,
    source_record_id UUID NOT NULL,
    title STRING NOT NULL,
    subtitle STRING NOT NULL DEFAULT '',
    route_path STRING NOT NULL,
    search_text STRING NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (id),
    CONSTRAINT uq_global_search_source UNIQUE (tenant_id, source_type, source_record_id)
);

CREATE INDEX IF NOT EXISTS idx_global_search_trgm ON global_search_documents USING GIN (search_text gin_trgm_ops);
