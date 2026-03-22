-- Generic data-audit columns (Phase 1); nullable for backward compatibility with existing rows.
ALTER TABLE audit_log ADD COLUMN IF NOT EXISTS source_service VARCHAR(64);
ALTER TABLE audit_log ADD COLUMN IF NOT EXISTS operation VARCHAR(32);
ALTER TABLE audit_log ADD COLUMN IF NOT EXISTS correlation_id UUID;

CREATE INDEX IF NOT EXISTS idx_audit_log_tenant_resource_time
    ON audit_log (tenant_id, resource_type, resource_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_audit_log_tenant_time
    ON audit_log (tenant_id, created_at DESC);
