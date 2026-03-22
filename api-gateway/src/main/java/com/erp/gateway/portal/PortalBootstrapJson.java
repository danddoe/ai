package com.erp.gateway.portal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PortalBootstrapJson {

    private UUID tenantId;
    private UUID companyId;
    private UUID defaultBuId;
    private String scope;

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public void setCompanyId(UUID companyId) {
        this.companyId = companyId;
    }

    public UUID getDefaultBuId() {
        return defaultBuId;
    }

    public void setDefaultBuId(UUID defaultBuId) {
        this.defaultBuId = defaultBuId;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }
}
