package com.erp.entitybuilder.web.v1;

import com.erp.entitybuilder.service.RecordsService;
import com.erp.entitybuilder.web.v1.dto.PageResponse;
import com.erp.entitybuilder.web.v1.dto.RecordDtos;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/tenants/{tenantId}/entities/{entityId}/records")
public class RecordsController {

    private final RecordsService recordsService;

    public RecordsController(RecordsService recordsService) {
        this.recordsService = recordsService;
    }

    @PostMapping
    @PreAuthorize("(hasAuthority('entity_builder:records:write') and @entityBuilderSecurity.isTenant(#tenantId)) or @entityBuilderSecurity.hasCrossTenantAdminAuthority()")
    public ResponseEntity<RecordDtos.RecordDto> create(
            @PathVariable UUID tenantId,
            @PathVariable UUID entityId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody RecordDtos.CreateRecordRequest req
    ) {
        UUID userId = SecurityUtil.principal().getUserId();
        boolean piiReadPermission = SecurityUtil.hasAuthority("entity_builder:pii:read");

        List<RecordsService.LinkInput> links = req.getLinks() != null
                ? req.getLinks().stream().map(l -> new RecordsService.LinkInput(l.getRelationshipSlug(), l.getToRecordId())).toList()
                : null;

        RecordsService.RecordResponse rr = recordsService.createRecord(
                tenantId,
                userId,
                entityId,
                req.getExternalId(),
                req.getValues(),
                links,
                idempotencyKey,
                piiReadPermission
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(rr.getRecordDto());
    }

    @GetMapping
    @PreAuthorize("(hasAuthority('entity_builder:records:read') and @entityBuilderSecurity.isTenant(#tenantId)) or @entityBuilderSecurity.hasCrossTenantAdminAuthority()")
    public PageResponse<RecordDtos.RecordDto> list(
            @PathVariable UUID tenantId,
            @PathVariable UUID entityId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize
    ) {
        boolean piiReadPermission = SecurityUtil.hasAuthority("entity_builder:pii:read");
        var pr = recordsService.listRecords(tenantId, entityId, page, pageSize, piiReadPermission);
        return new PageResponse<>(pr.items(), pr.page(), pr.pageSize(), pr.total());
    }

    @GetMapping("/{recordId}")
    @PreAuthorize("(hasAuthority('entity_builder:records:read') and @entityBuilderSecurity.isTenant(#tenantId)) or @entityBuilderSecurity.hasCrossTenantAdminAuthority()")
    public RecordDtos.RecordDto get(
            @PathVariable UUID tenantId,
            @PathVariable UUID entityId,
            @PathVariable UUID recordId
    ) {
        boolean piiReadPermission = SecurityUtil.hasAuthority("entity_builder:pii:read");
        RecordsService.RecordResponse rr = recordsService.getRecord(tenantId, recordId, piiReadPermission);
        return rr.getRecordDto();
    }

    @PatchMapping("/{recordId}")
    @PreAuthorize("(hasAuthority('entity_builder:records:write') and @entityBuilderSecurity.isTenant(#tenantId)) or @entityBuilderSecurity.hasCrossTenantAdminAuthority()")
    public RecordDtos.RecordDto update(
            @PathVariable UUID tenantId,
            @PathVariable UUID entityId,
            @PathVariable UUID recordId,
            @Valid @RequestBody RecordDtos.UpdateRecordRequest req
    ) {
        UUID userId = SecurityUtil.principal().getUserId();
        boolean piiReadPermission = SecurityUtil.hasAuthority("entity_builder:pii:read");
        var rr = recordsService.updateRecord(tenantId, userId, entityId, recordId, req.getValues(),
                req.getLinks() != null ? req.getLinks().stream().map(l -> new RecordsService.LinkInput(l.getRelationshipSlug(), l.getToRecordId())).toList() : null,
                piiReadPermission);
        return rr.getRecordDto();
    }

    @DeleteMapping("/{recordId}")
    @PreAuthorize("(hasAuthority('entity_builder:records:write') and @entityBuilderSecurity.isTenant(#tenantId)) or @entityBuilderSecurity.hasCrossTenantAdminAuthority()")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable UUID tenantId,
            @PathVariable UUID entityId,
            @PathVariable UUID recordId
    ) {
        recordsService.deleteRecord(tenantId, entityId, recordId);
    }
}

