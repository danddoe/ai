-- Portal hostname → tenant / company / BU context (custom domains / CNAME)

ALTER TABLE companies
    ADD COLUMN IF NOT EXISTS default_portal_bu_id UUID REFERENCES business_units (bu_id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_companies_default_portal_bu_id ON companies (default_portal_bu_id);

CREATE TABLE IF NOT EXISTS portal_host_bindings (
    binding_id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    hostname VARCHAR(253) NOT NULL,
    scope VARCHAR(32) NOT NULL,
    tenant_id UUID NOT NULL,
    company_id UUID REFERENCES companies (company_id) ON DELETE CASCADE,
    bu_id UUID REFERENCES business_units (bu_id) ON DELETE CASCADE,
    verified_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_portal_host_bindings_scope CHECK (
        (scope = 'TENANT' AND company_id IS NULL AND bu_id IS NULL)
        OR (scope = 'COMPANY' AND company_id IS NOT NULL AND bu_id IS NULL)
        OR (scope = 'BUSINESS_UNIT' AND company_id IS NOT NULL AND bu_id IS NOT NULL)
    ),
    CONSTRAINT chk_portal_host_bindings_scope_value CHECK (scope IN ('TENANT', 'COMPANY', 'BUSINESS_UNIT'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_portal_host_bindings_hostname ON portal_host_bindings (hostname);

CREATE INDEX IF NOT EXISTS idx_portal_host_bindings_tenant_id ON portal_host_bindings (tenant_id);
