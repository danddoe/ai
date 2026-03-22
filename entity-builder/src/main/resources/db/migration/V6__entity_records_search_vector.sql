-- Denormalized text for global lookup / typeahead (single search vector pattern).
ALTER TABLE entity_records ADD COLUMN IF NOT EXISTS search_vector STRING NOT NULL DEFAULT '';

-- Trigram index for ILIKE / substring search (CockroachDB: see https://www.cockroachlabs.com/docs/stable/trigram-indexes )
CREATE INDEX IF NOT EXISTS idx_entity_records_search_trgm ON entity_records USING GIN (search_vector gin_trgm_ops);

-- Default field slug for lookup display labels (validated in application layer).
ALTER TABLE entities ADD COLUMN IF NOT EXISTS default_display_field_slug VARCHAR(100) NULL;
