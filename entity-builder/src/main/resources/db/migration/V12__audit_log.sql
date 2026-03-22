-- Append-only audit log for data changes (shape aligned with IAM; no FKs so standalone entity-builder DBs work).
CREATE TABLE IF NOT EXISTS audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID,
    actor_id UUID,
    action VARCHAR(100) NOT NULL,
    resource_type VARCHAR(100),
    resource_id UUID,
    payload JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    source_service VARCHAR(64),
    operation VARCHAR(32),
    correlation_id UUID
);

CREATE INDEX IF NOT EXISTS idx_audit_log_tenant_created ON audit_log(tenant_id, created_at);
CREATE INDEX IF NOT EXISTS idx_audit_log_actor ON audit_log(actor_id, created_at);
CREATE INDEX IF NOT EXISTS idx_audit_log_resource ON audit_log(resource_type, resource_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_tenant_resource_time ON audit_log(tenant_id, resource_type, resource_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_log_tenant_time ON audit_log(tenant_id, created_at DESC);
