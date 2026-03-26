package com.erp.entitybuilder.web.v1;

import com.erp.entitybuilder.service.ddl.DdlEntityImportService;
import com.erp.entitybuilder.web.v1.dto.DdlImportDtos;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * DDL → entity schema import. Lives in its own controller so paths under {@code /import/} are not
 * affected by {@code /v1/entities/{entityId}} routing on {@link EntitiesController}.
 */
@RestController
@RequestMapping("/v1/entities/import")
public class DdlEntityImportController {

    private final DdlEntityImportService ddlEntityImportService;

    public DdlEntityImportController(DdlEntityImportService ddlEntityImportService) {
        this.ddlEntityImportService = ddlEntityImportService;
    }

    @PostMapping("/ddl/preview")
    @PreAuthorize("@entityBuilderSecurity.canReadSchema()")
    public DdlImportDtos.DdlImportPreviewResponse previewDdlImport(@Valid @RequestBody DdlImportDtos.DdlImportPreviewRequest req) {
        UUID tenantId = SecurityUtil.principal().getTenantId();
        return ddlEntityImportService.preview(tenantId, req);
    }

    /** Creates entities, fields, and optionally relationship rows from DDL. */
    @PostMapping("/ddl")
    @PreAuthorize("@entityBuilderSecurity.canWriteFullSchema()")
    public DdlImportDtos.DdlImportApplyResponse applyDdlImport(@Valid @RequestBody DdlImportDtos.DdlImportApplyRequest req) {
        UUID tenantId = SecurityUtil.principal().getTenantId();
        return ddlEntityImportService.apply(tenantId, req);
    }
}
