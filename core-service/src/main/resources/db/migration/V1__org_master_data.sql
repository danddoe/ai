-- Master data: countries (global), org hierarchy, locations, properties, units

CREATE TABLE IF NOT EXISTS countries (
    country_code CHAR(2) NOT NULL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    alpha3 CHAR(3),
    numeric_code CHAR(3)
);

CREATE TABLE IF NOT EXISTS companies (
    company_id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id UUID NOT NULL,
    parent_company_id UUID REFERENCES companies (company_id) ON DELETE RESTRICT,
    company_name VARCHAR(255) NOT NULL,
    ownership_pct DECIMAL(5, 2),
    base_currency CHAR(3) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_companies_tenant_id ON companies (tenant_id);
CREATE INDEX IF NOT EXISTS idx_companies_parent_company_id ON companies (parent_company_id);

CREATE TABLE IF NOT EXISTS company_hierarchy_history (
    hierarchy_id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id UUID NOT NULL,
    parent_company_id UUID REFERENCES companies (company_id) ON DELETE RESTRICT,
    child_company_id UUID NOT NULL REFERENCES companies (company_id) ON DELETE CASCADE,
    ownership_pct DECIMAL(5, 2),
    start_date DATE NOT NULL,
    end_date DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_company_hierarchy_history_tenant_child
    ON company_hierarchy_history (tenant_id, child_company_id, start_date);

CREATE UNIQUE INDEX IF NOT EXISTS uq_company_hierarchy_open_interval
    ON company_hierarchy_history (tenant_id, child_company_id)
    WHERE end_date IS NULL;

CREATE TABLE IF NOT EXISTS business_units (
    bu_id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id UUID NOT NULL,
    company_id UUID NOT NULL REFERENCES companies (company_id) ON DELETE CASCADE,
    parent_bu_id UUID REFERENCES business_units (bu_id) ON DELETE RESTRICT,
    bu_type VARCHAR(100) NOT NULL,
    bu_name VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_business_units_tenant_id ON business_units (tenant_id);
CREATE INDEX IF NOT EXISTS idx_business_units_company_id ON business_units (company_id);
CREATE INDEX IF NOT EXISTS idx_business_units_parent_bu_id ON business_units (parent_bu_id);

CREATE TABLE IF NOT EXISTS bu_hierarchy_history (
    hierarchy_id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id UUID NOT NULL,
    parent_bu_id UUID REFERENCES business_units (bu_id) ON DELETE RESTRICT,
    child_bu_id UUID NOT NULL REFERENCES business_units (bu_id) ON DELETE CASCADE,
    start_date DATE NOT NULL,
    end_date DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_bu_hierarchy_history_tenant_child
    ON bu_hierarchy_history (tenant_id, child_bu_id, start_date);

CREATE UNIQUE INDEX IF NOT EXISTS uq_bu_hierarchy_open_interval
    ON bu_hierarchy_history (tenant_id, child_bu_id)
    WHERE end_date IS NULL;

CREATE TABLE IF NOT EXISTS regions (
    region_id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id UUID NOT NULL,
    parent_region_id UUID REFERENCES regions (region_id) ON DELETE RESTRICT,
    region_code VARCHAR(64) NOT NULL,
    region_name VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, region_code)
);

CREATE INDEX IF NOT EXISTS idx_regions_tenant_id ON regions (tenant_id);
CREATE INDEX IF NOT EXISTS idx_regions_parent_region_id ON regions (parent_region_id);

CREATE TABLE IF NOT EXISTS locations (
    location_id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id UUID NOT NULL,
    location_name VARCHAR(255) NOT NULL,
    address_line_1 VARCHAR(500),
    city VARCHAR(255),
    state_province VARCHAR(100),
    postal_code VARCHAR(32),
    country_code CHAR(2) NOT NULL REFERENCES countries (country_code),
    region_id UUID REFERENCES regions (region_id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_locations_tenant_id ON locations (tenant_id);
CREATE INDEX IF NOT EXISTS idx_locations_region_id ON locations (region_id);

CREATE TABLE IF NOT EXISTS properties (
    property_id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id UUID NOT NULL,
    company_id UUID NOT NULL REFERENCES companies (company_id) ON DELETE CASCADE,
    location_id UUID NOT NULL REFERENCES locations (location_id) ON DELETE RESTRICT,
    property_name VARCHAR(255) NOT NULL,
    property_type VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_properties_tenant_id ON properties (tenant_id);
CREATE INDEX IF NOT EXISTS idx_properties_company_id ON properties (company_id);
CREATE INDEX IF NOT EXISTS idx_properties_location_id ON properties (location_id);

CREATE TABLE IF NOT EXISTS property_units (
    unit_id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id UUID NOT NULL,
    property_id UUID NOT NULL REFERENCES properties (property_id) ON DELETE CASCADE,
    unit_number VARCHAR(64) NOT NULL,
    square_footage DECIMAL(18, 4),
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, property_id, unit_number)
);

CREATE INDEX IF NOT EXISTS idx_property_units_tenant_id ON property_units (tenant_id);
CREATE INDEX IF NOT EXISTS idx_property_units_property_id ON property_units (property_id);
