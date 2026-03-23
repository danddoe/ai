package com.erp.coreservice.audit;

import com.erp.coreservice.domain.BusinessUnit;
import com.erp.coreservice.domain.Company;
import com.erp.coreservice.domain.Location;
import com.erp.coreservice.domain.PortalHostBinding;
import com.erp.coreservice.domain.Property;
import com.erp.coreservice.domain.PropertyUnit;
import com.erp.coreservice.domain.Region;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Stable key sets for before/after audit rows (no created_at / updated_at).
 */
public final class CoreEntityAuditSnapshots {

    private CoreEntityAuditSnapshots() {
    }

    public static Map<String, Object> company(Company c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("companyId", c.getCompanyId());
        m.put("tenantId", c.getTenantId());
        m.put("parentCompanyId", c.getParentCompanyId());
        m.put("companyName", c.getCompanyName());
        m.put("slug", c.getSlug());
        m.put("alias", c.getAlias());
        m.put("ownershipPct", c.getOwnershipPct());
        m.put("baseCurrency", c.getBaseCurrency());
        m.put("defaultPortalBuId", c.getDefaultPortalBuId());
        return m;
    }

    public static Map<String, Object> businessUnit(BusinessUnit bu) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("buId", bu.getBuId());
        m.put("tenantId", bu.getTenantId());
        m.put("companyId", bu.getCompanyId());
        m.put("parentBuId", bu.getParentBuId());
        m.put("buType", bu.getBuType());
        m.put("buName", bu.getBuName());
        m.put("slug", bu.getSlug());
        m.put("alias", bu.getAlias());
        return m;
    }

    public static Map<String, Object> location(Location loc) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("locationId", loc.getLocationId());
        m.put("tenantId", loc.getTenantId());
        m.put("locationName", loc.getLocationName());
        m.put("addressLine1", loc.getAddressLine1());
        m.put("city", loc.getCity());
        m.put("stateProvince", loc.getStateProvince());
        m.put("postalCode", loc.getPostalCode());
        m.put("countryCode", loc.getCountryCode());
        m.put("regionId", loc.getRegionId());
        return m;
    }

    public static Map<String, Object> region(Region r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("regionId", r.getRegionId());
        m.put("tenantId", r.getTenantId());
        m.put("parentRegionId", r.getParentRegionId());
        m.put("regionCode", r.getRegionCode());
        m.put("regionName", r.getRegionName());
        return m;
    }

    public static Map<String, Object> property(Property p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("propertyId", p.getPropertyId());
        m.put("tenantId", p.getTenantId());
        m.put("companyId", p.getCompanyId());
        m.put("locationId", p.getLocationId());
        m.put("propertyName", p.getPropertyName());
        m.put("propertyType", p.getPropertyType());
        return m;
    }

    public static Map<String, Object> propertyUnit(PropertyUnit u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("unitId", u.getUnitId());
        m.put("tenantId", u.getTenantId());
        m.put("propertyId", u.getPropertyId());
        m.put("unitNumber", u.getUnitNumber());
        m.put("squareFootage", u.getSquareFootage());
        m.put("status", u.getStatus());
        return m;
    }

    public static Map<String, Object> portalHostBinding(PortalHostBinding b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("bindingId", b.getBindingId());
        m.put("hostname", b.getHostname());
        m.put("slug", b.getSlug());
        m.put("scope", b.getScope() != null ? b.getScope().name() : null);
        m.put("tenantId", b.getTenantId());
        m.put("companyId", b.getCompanyId());
        m.put("buId", b.getBuId());
        m.put("verifiedAt", b.getVerifiedAt() != null ? b.getVerifiedAt().toString() : null);
        return m;
    }
}
