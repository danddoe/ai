package com.erp.entitybuilder.web.v1;

import com.erp.entitybuilder.service.EntityRecordAuditService;
import com.erp.entitybuilder.web.v1.dto.AuditEventDtos;
import com.erp.entitybuilder.web.v1.dto.PageResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Read-only audit timeline for dynamic entity records ({@code audit_log} rows).
 */
@RestController
@RequestMapping("/v1/tenants/{tenantId}/entities/{entityId}")
public class EntityRecordAuditController {

    private final EntityRecordAuditService auditService;

    public EntityRecordAuditController(EntityRecordAuditService auditService) {
        this.auditService = auditService;
    }

    /**
     * All audit events for every record of this entity (payload.context.entityId match).
     */
    @GetMapping("/audit-events")
    @PreAuthorize("(hasAuthority('entity_builder:records:read') and @entityBuilderSecurity.isTenant(#tenantId)) or @entityBuilderSecurity.hasCrossTenantAdminAuthority()")
    public PageResponse<AuditEventDtos.AuditEventDto> listForEntity(
            @PathVariable UUID tenantId,
            @PathVariable UUID entityId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String actionPrefix
    ) {
        return auditService.listForEntity(tenantId, entityId, page, pageSize, from, to, actionPrefix);
    }

    /**
     * Audit timeline for a single record.
     */
    @GetMapping("/records/{recordId}/audit-events")
    @PreAuthorize("(hasAuthority('entity_builder:records:read') and @entityBuilderSecurity.isTenant(#tenantId)) or @entityBuilderSecurity.hasCrossTenantAdminAuthority()")
    public PageResponse<AuditEventDtos.AuditEventDto> listForRecord(
            @PathVariable UUID tenantId,
            @PathVariable UUID entityId,
            @PathVariable UUID recordId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String actionPrefix
    ) {
        return auditService.listForRecord(tenantId, entityId, recordId, page, pageSize, from, to, actionPrefix);
    }
}
