package com.erp.entitybuilder.web.v1;

import com.erp.entitybuilder.service.EntityStatusAssignmentService;
import com.erp.entitybuilder.web.v1.dto.EntityStatusAssignmentDtos;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/tenants/{tenantId}/entities/{entityDefinitionId}/entity-status-assignments")
public class EntityStatusAssignmentsController {

    private final EntityStatusAssignmentService assignmentService;

    public EntityStatusAssignmentsController(EntityStatusAssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    @GetMapping
    @PreAuthorize("@entityBuilderSecurity.canReadSchema() and @entityBuilderSecurity.isTenant(#tenantId)")
    public List<EntityStatusAssignmentDtos.AssignmentRowDto> list(
            @PathVariable UUID tenantId,
            @PathVariable UUID entityDefinitionId
    ) {
        return assignmentService.listAssignments(tenantId, entityDefinitionId);
    }

    @GetMapping("/available")
    @PreAuthorize("@entityBuilderSecurity.canReadSchema() and @entityBuilderSecurity.isTenant(#tenantId)")
    public List<EntityStatusAssignmentDtos.AvailableStatusDto> available(
            @PathVariable UUID tenantId,
            @PathVariable UUID entityDefinitionId
    ) {
        return assignmentService.listAssignableStatuses(tenantId, entityDefinitionId);
    }

    @PutMapping
    @PreAuthorize("@entityBuilderSecurity.canWriteTenantSchema() and @entityBuilderSecurity.isTenant(#tenantId)")
    public List<EntityStatusAssignmentDtos.AssignmentRowDto> replace(
            @PathVariable UUID tenantId,
            @PathVariable UUID entityDefinitionId,
            @RequestBody(required = false) EntityStatusAssignmentDtos.ReplaceAssignmentsRequest body
    ) {
        List<UUID> ids = body == null ? null : body.entityStatusIds();
        return assignmentService.replaceAssignments(tenantId, entityDefinitionId, ids);
    }
}
