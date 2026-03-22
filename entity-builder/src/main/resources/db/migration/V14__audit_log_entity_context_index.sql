-- Entity-wide audit listing filters on payload.context.entityId (see EntityRecordAuditService).
CREATE INDEX IF NOT EXISTS idx_audit_log_tenant_ctx_entity_created
    ON audit_log (tenant_id, (payload->'context'->>'entityId'), created_at DESC);
