package com.erp.entitybuilder.web.v1;

import com.erp.entitybuilder.service.EntityStatusAssignmentService;
import com.erp.entitybuilder.web.v1.dto.EntityStatusAssignmentDtos;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Field-scoped status assignments: {@code scope_id} = {@code entity_fields.id}, validated against the owning entity.
 */
@RestController
@RequestMapping("/v1/entities/{entityId}/fields/{fieldId}/entity-status-assignments")
public class EntityFieldEntityStatusAssignmentsController {

    private final EntityStatusAssignmentService assignmentService;

    public EntityFieldEntityStatusAssignmentsController(EntityStatusAssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    @GetMapping
    @PreAuthorize("@entityBuilderSecurity.canReadSchema()")
    public List<EntityStatusAssignmentDtos.AssignmentRowDto> list(
            @PathVariable UUID entityId,
            @PathVariable UUID fieldId
    ) {
        UUID tenantId = SecurityUtil.principal().getTenantId();
        return assignmentService.listAssignmentsForField(tenantId, entityId, fieldId);
    }

    @GetMapping("/available")
    @PreAuthorize("@entityBuilderSecurity.canReadSchema()")
    public List<EntityStatusAssignmentDtos.AvailableStatusDto> available(
            @PathVariable UUID entityId,
            @PathVariable UUID fieldId
    ) {
        UUID tenantId = SecurityUtil.principal().getTenantId();
        return assignmentService.listAssignableStatusesForField(tenantId, entityId, fieldId);
    }

    @PutMapping
    @PreAuthorize("@entityBuilderSecurity.canWriteTenantSchema()")
    public List<EntityStatusAssignmentDtos.AssignmentRowDto> replace(
            @PathVariable UUID entityId,
            @PathVariable UUID fieldId,
            @RequestBody(required = false) EntityStatusAssignmentDtos.ReplaceAssignmentsRequest body
    ) {
        UUID tenantId = SecurityUtil.principal().getTenantId();
        List<UUID> ids = body == null ? null : body.entityStatusIds();
        return assignmentService.replaceAssignmentsForField(tenantId, entityId, fieldId, ids);
    }
}
