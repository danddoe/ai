package com.erp.entitybuilder.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.UUID;

/**
 * Tenant UUID for platform-owned rows (e.g. IAM bootstrap "ai" tenant). Required for entity-status provisioning and STANDARD_RECORD visibility.
 */
@ConfigurationProperties(prefix = "entitybuilder.platform")
public class PlatformTenantProperties {

    /**
     * When empty, STANDARD_RECORD features and provisioning are disabled until configured.
     */
    private UUID tenantId;

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public boolean isConfigured() {
        return tenantId != null;
    }
}
