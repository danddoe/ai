package com.erp.coreservice.portal;

import com.erp.coreservice.domain.PortalHostBindingScope;

import java.util.UUID;

public record PortalBootstrapContext(
        UUID tenantId,
        UUID companyId,
        UUID defaultBuId,
        PortalHostBindingScope scope
) {
    public static PortalBootstrapContext empty() {
        return new PortalBootstrapContext(null, null, null, null);
    }
}
