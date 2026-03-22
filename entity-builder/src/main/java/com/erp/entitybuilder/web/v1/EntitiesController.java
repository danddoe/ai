package com.erp.entitybuilder.web.v1;

import com.erp.entitybuilder.domain.EntityDefinition;
import com.erp.entitybuilder.service.EntitySchemaService;
import com.erp.entitybuilder.web.v1.dto.EntityDtos;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/v1/entities")
public class EntitiesController {

    private final EntitySchemaService schemaService;

    public EntitiesController(EntitySchemaService schemaService) {
        this.schemaService = schemaService;
    }

    @GetMapping
    @PreAuthorize("@entityBuilderSecurity.canReadEntitiesForPortalUi()")
    public List<EntityDtos.EntityDto> list(
            @RequestParam(required = false) String categoryKey,
            @RequestParam(required = false) String q
    ) {
        UUID tenantId = SecurityUtil.principal().getTenantId();
        return schemaService.listEntities(tenantId, Optional.ofNullable(categoryKey), Optional.ofNullable(q)).stream()
                .map(EntitiesController::toDto)
                .toList();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('entity_builder:schema:write')")
    public EntityDtos.EntityDto create(@Valid @RequestBody EntityDtos.CreateEntityRequest req) {
        UUID tenantId = SecurityUtil.principal().getTenantId();
        EntityDefinition e = schemaService.createEntity(
                tenantId, req.getName(), req.getSlug(), req.getDescription(), req.getStatus(), req.getCategoryKey());
        return toDto(e);
    }

    @GetMapping("/by-slug/{slug}")
    @PreAuthorize("hasAuthority('entity_builder:schema:read')")
    public EntityDtos.EntityDto getBySlug(@PathVariable String slug) {
        UUID tenantId = SecurityUtil.principal().getTenantId();
        EntityDefinition e = schemaService.getEntityBySlug(tenantId, slug);
        return toDto(e);
    }

    @GetMapping("/{entityId}")
    @PreAuthorize("@entityBuilderSecurity.canReadEntitiesForPortalUi()")
    public EntityDtos.EntityDto get(@PathVariable UUID entityId) {
        UUID tenantId = SecurityUtil.principal().getTenantId();
        EntityDefinition e = schemaService.getEntity(tenantId, entityId);
        return toDto(e);
    }

    @PatchMapping("/{entityId}")
    @PreAuthorize("hasAuthority('entity_builder:schema:write')")
    public EntityDtos.EntityDto update(@PathVariable UUID entityId, @Valid @RequestBody EntityDtos.UpdateEntityRequest req) {
        UUID tenantId = SecurityUtil.principal().getTenantId();
        EntityDefinition e = schemaService.updateEntity(
                tenantId,
                entityId,
                Optional.ofNullable(req.getName()),
                Optional.ofNullable(req.getSlug()),
                Optional.ofNullable(req.getDescription()),
                Optional.ofNullable(req.getStatus()),
                Boolean.TRUE.equals(req.getClearDefaultDisplayField()),
                Optional.ofNullable(req.getDefaultDisplayFieldSlug()),
                Boolean.TRUE.equals(req.getClearCategoryKey()),
                Optional.ofNullable(req.getCategoryKey())
        );
        return toDto(e);
    }

    @DeleteMapping("/{entityId}")
    @PreAuthorize("hasAuthority('entity_builder:schema:write')")
    public void delete(@PathVariable UUID entityId) {
        UUID tenantId = SecurityUtil.principal().getTenantId();
        schemaService.deleteEntity(tenantId, entityId);
    }

    private static EntityDtos.EntityDto toDto(EntityDefinition e) {
        return new EntityDtos.EntityDto(
                e.getId(),
                e.getTenantId(),
                e.getName(),
                e.getSlug(),
                e.getDescription(),
                e.getBaseEntityId(),
                e.getDefaultDisplayFieldSlug(),
                e.getStatus(),
                e.getCategoryKey(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }
}

