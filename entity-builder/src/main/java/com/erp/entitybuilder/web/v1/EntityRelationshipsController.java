package com.erp.entitybuilder.web.v1;

import com.erp.entitybuilder.domain.EntityRelationship;
import com.erp.entitybuilder.service.RelationshipSchemaService;
import com.erp.entitybuilder.web.v1.dto.RelationshipDtos;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/v1/entity-relationships")
public class EntityRelationshipsController {

    private final RelationshipSchemaService relationshipService;

    public EntityRelationshipsController(RelationshipSchemaService relationshipService) {
        this.relationshipService = relationshipService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('entity_builder:schema:read')")
    public List<RelationshipDtos.RelationshipDto> list() {
        UUID tenantId = SecurityUtil.principal().getTenantId();
        return relationshipService.list(tenantId).stream().map(this::toDto).toList();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('entity_builder:schema:write')")
    public RelationshipDtos.RelationshipDto create(@Valid @RequestBody RelationshipDtos.CreateRelationshipRequest req) {
        UUID tenantId = SecurityUtil.principal().getTenantId();
        EntityRelationship r = relationshipService.create(
                tenantId,
                req.getName(),
                req.getSlug(),
                req.getCardinality(),
                req.getFromEntityId(),
                req.getToEntityId(),
                req.getFromFieldSlug(),
                req.getToFieldSlug()
        );
        return toDto(r);
    }

    @GetMapping("/{relationshipId}")
    @PreAuthorize("hasAuthority('entity_builder:schema:read')")
    public RelationshipDtos.RelationshipDto get(@PathVariable UUID relationshipId) {
        UUID tenantId = SecurityUtil.principal().getTenantId();
        return toDto(relationshipService.get(tenantId, relationshipId));
    }

    @PatchMapping("/{relationshipId}")
    @PreAuthorize("hasAuthority('entity_builder:schema:write')")
    public RelationshipDtos.RelationshipDto update(
            @PathVariable UUID relationshipId,
            @Valid @RequestBody RelationshipDtos.UpdateRelationshipRequest req
    ) {
        UUID tenantId = SecurityUtil.principal().getTenantId();
        return toDto(relationshipService.update(
                tenantId,
                relationshipId,
                Optional.ofNullable(req.getName()),
                Optional.ofNullable(req.getSlug()),
                Optional.ofNullable(req.getCardinality()),
                Optional.ofNullable(req.getFromFieldSlug()),
                Optional.ofNullable(req.getToFieldSlug())
        ));
    }

    @DeleteMapping("/{relationshipId}")
    @PreAuthorize("hasAuthority('entity_builder:schema:write')")
    public void delete(@PathVariable UUID relationshipId) {
        UUID tenantId = SecurityUtil.principal().getTenantId();
        relationshipService.delete(tenantId, relationshipId);
    }

    private RelationshipDtos.RelationshipDto toDto(EntityRelationship r) {
        return new RelationshipDtos.RelationshipDto(
                r.getId(),
                r.getTenantId(),
                r.getName(),
                r.getSlug(),
                r.getFromEntityId(),
                r.getToEntityId(),
                r.getFromFieldSlug(),
                r.getToFieldSlug(),
                r.getCardinality(),
                r.getCreatedAt(),
                r.getUpdatedAt()
        );
    }
}

