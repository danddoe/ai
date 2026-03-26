package com.erp.entitybuilder.config;

import com.erp.entitybuilder.security.BootstrapSchemaSecurity;
import com.erp.entitybuilder.service.catalog.EntityStatusDynamicEntityProvisioner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** After the app is ready, idempotently provisions {@code entity_status} for the configured platform tenant. */
@Component
public class EntityStatusStartupProvisioner {

    private static final Logger log = LoggerFactory.getLogger(EntityStatusStartupProvisioner.class);

    private final PlatformTenantProperties platformTenantProperties;
    private final EntityStatusDynamicEntityProvisioner entityStatusDynamicEntityProvisioner;

    public EntityStatusStartupProvisioner(
            PlatformTenantProperties platformTenantProperties,
            EntityStatusDynamicEntityProvisioner entityStatusDynamicEntityProvisioner
    ) {
        this.platformTenantProperties = platformTenantProperties;
        this.entityStatusDynamicEntityProvisioner = entityStatusDynamicEntityProvisioner;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady(ApplicationReadyEvent event) {
        if (!platformTenantProperties.isConfigured()) {
            log.debug("Skipping entity_status startup provision: entitybuilder.platform.tenant-id is not set");
            return;
        }
        UUID tid = platformTenantProperties.getTenantId();
        try {
            BootstrapSchemaSecurity.runWithFullSchemaWrite(tid, entityStatusDynamicEntityProvisioner::ensureProvisioned);
            log.info("Entity status catalog ensured for platform tenant {}", tid);
        } catch (Exception ex) {
            log.error("Failed to ensure entity_status catalog on startup for platform tenant {}", tid, ex);
            throw ex;
        }
    }
}
