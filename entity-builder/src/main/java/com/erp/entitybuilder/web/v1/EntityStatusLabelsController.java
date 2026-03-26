package com.erp.entitybuilder.web.v1;

import com.erp.entitybuilder.service.EntityStatusLabelService;
import com.erp.entitybuilder.web.v1.dto.EntityFieldDtos;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/entity-status/{entityStatusId}/labels")
public class EntityStatusLabelsController {

    private final EntityStatusLabelService entityStatusLabelService;

    public EntityStatusLabelsController(EntityStatusLabelService entityStatusLabelService) {
        this.entityStatusLabelService = entityStatusLabelService;
    }

    @GetMapping
    @PreAuthorize("@entityBuilderSecurity.canReadSchema()")
    public Map<String, String> list(@PathVariable UUID entityStatusId) {
        UUID tenantId = SecurityUtil.principal().getTenantId();
        return entityStatusLabelService.listLabelsForRequester(tenantId, entityStatusId);
    }

    @PutMapping("/{locale}")
    @PreAuthorize("@entityBuilderSecurity.canWriteTenantSchema()")
    public Map<String, String> put(
            @PathVariable UUID entityStatusId,
            @PathVariable String locale,
            @RequestBody(required = false) EntityFieldDtos.UpsertFieldLabelRequest body
    ) {
        UUID tenantId = SecurityUtil.principal().getTenantId();
        String label = body != null ? body.label() : null;
        entityStatusLabelService.upsertLabel(tenantId, entityStatusId, locale, label);
        return entityStatusLabelService.listLabelsForRequester(tenantId, entityStatusId);
    }
}
