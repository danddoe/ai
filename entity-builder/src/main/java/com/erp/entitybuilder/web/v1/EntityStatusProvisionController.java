package com.erp.entitybuilder.web.v1;

import com.erp.entitybuilder.config.PlatformTenantProperties;
import com.erp.entitybuilder.service.catalog.EntityStatusDynamicEntityProvisioner;
import com.erp.entitybuilder.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/tenants/{tenantId}/platform")
public class EntityStatusProvisionController {

    private final EntityStatusDynamicEntityProvisioner provisioner;
    private final PlatformTenantProperties platformTenantProperties;

    public EntityStatusProvisionController(
            EntityStatusDynamicEntityProvisioner provisioner,
            PlatformTenantProperties platformTenantProperties
    ) {
        this.provisioner = provisioner;
        this.platformTenantProperties = platformTenantProperties;
    }

    /**
     * Idempotently provisions {@code entity_status} schema and STANDARD_RECORD seed data for the platform tenant.
     * Path {@code tenantId} must match {@code entitybuilder.platform.tenant-id}.
     */
    @PostMapping("/entity-status/ensure")
    @PreAuthorize("hasAuthority('entity_builder:schema:write') and @entityBuilderSecurity.isTenant(#tenantId)")
    public ResponseEntity<Void> ensure(@PathVariable UUID tenantId) {
        if (!platformTenantProperties.isConfigured()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request",
                    "entitybuilder.platform.tenant-id is not configured");
        }
        if (!platformTenantProperties.getTenantId().equals(tenantId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "forbidden",
                    "tenantId must match platform tenant", Map.of("tenantId", tenantId));
        }
        provisioner.ensureProvisioned();
        return ResponseEntity.ok().build();
    }
}
