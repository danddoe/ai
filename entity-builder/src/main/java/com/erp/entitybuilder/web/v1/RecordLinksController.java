package com.erp.entitybuilder.web.v1;

import com.erp.entitybuilder.service.RecordsService;
import com.erp.entitybuilder.web.v1.dto.RecordDtos;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/tenants/{tenantId}/records/{recordId}/links")
public class RecordLinksController {

    private final RecordsService recordsService;

    public RecordLinksController(RecordsService recordsService) {
        this.recordsService = recordsService;
    }

    @GetMapping
    @PreAuthorize("(hasAuthority('entity_builder:records:read') and @entityBuilderSecurity.isTenant(#tenantId)) or @entityBuilderSecurity.hasCrossTenantAdminAuthority()")
    public List<RecordDtos.LinkDto> list(
            @PathVariable UUID tenantId,
            @PathVariable UUID recordId
    ) {
        return recordsService.listLinks(tenantId, recordId).links();
    }

    @PostMapping
    @PreAuthorize("(hasAuthority('entity_builder:records:write') and @entityBuilderSecurity.isTenant(#tenantId)) or @entityBuilderSecurity.hasCrossTenantAdminAuthority()")
    public void create(
            @PathVariable UUID tenantId,
            @PathVariable UUID recordId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationIdHeader,
            @Valid @RequestBody RecordDtos.LinkInput req
    ) {
        UUID userId = SecurityUtil.principal().getUserId();
        recordsService.addLink(tenantId, userId, recordId, req.getRelationshipSlug(), req.getToRecordId(),
                AuditHttp.parseCorrelationId(correlationIdHeader));
    }

    @DeleteMapping
    @PreAuthorize("(hasAuthority('entity_builder:records:write') and @entityBuilderSecurity.isTenant(#tenantId)) or @entityBuilderSecurity.hasCrossTenantAdminAuthority()")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable UUID tenantId,
            @PathVariable UUID recordId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationIdHeader,
            @Valid @RequestBody RecordDtos.LinkInput req
    ) {
        UUID userId = SecurityUtil.principal().getUserId();
        recordsService.deleteLink(tenantId, userId, recordId, req.getRelationshipSlug(), req.getToRecordId(),
                AuditHttp.parseCorrelationId(correlationIdHeader));
    }
}

