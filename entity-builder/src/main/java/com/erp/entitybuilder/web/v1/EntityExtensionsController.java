package com.erp.entitybuilder.web.v1;

import com.erp.entitybuilder.domain.EntityDefinition;
import com.erp.entitybuilder.service.EntitySchemaService;
import com.erp.entitybuilder.web.v1.dto.EntityDtos;
import com.erp.entitybuilder.web.v1.dto.ExtensionDtos;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/entities/{baseEntityId}/extensions")
public class EntityExtensionsController {

    private final EntitySchemaService schemaService;

    public EntityExtensionsController(EntitySchemaService schemaService) {
        this.schemaService = schemaService;
    }

    @GetMapping
    @PreAuthorize("@entityBuilderSecurity.canReadSchema()")
    public List<EntityDtos.EntityDto> list(@PathVariable UUID baseEntityId) {
        UUID tenantId = SecurityUtil.principal().getTenantId();
        return schemaService.listExtensions(tenantId, baseEntityId).stream().map(this::toDto).toList();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('entity_builder:schema:write')")
    public EntityDtos.EntityDto create(
            @PathVariable UUID baseEntityId,
            @RequestBody ExtensionDtos.CreateExtensionRequest req
    ) {
        UUID tenantId = SecurityUtil.principal().getTenantId();
        EntityDefinition e = schemaService.createExtension(tenantId, baseEntityId, req.getName(), req.getSlug(), req.getDescription(), req.getStatus());
        return toDto(e);
    }

    @DeleteMapping("/{extensionEntityId}")
    @PreAuthorize("hasAuthority('entity_builder:schema:write')")
    public void delete(@PathVariable UUID baseEntityId, @PathVariable UUID extensionEntityId) {
        UUID tenantId = SecurityUtil.principal().getTenantId();
        schemaService.deleteExtension(tenantId, extensionEntityId);
    }

    private EntityDtos.EntityDto toDto(EntityDefinition e) {
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

