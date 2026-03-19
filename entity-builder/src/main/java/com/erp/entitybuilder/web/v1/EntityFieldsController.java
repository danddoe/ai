package com.erp.entitybuilder.web.v1;

import com.erp.entitybuilder.domain.EntityField;
import com.erp.entitybuilder.service.EntitySchemaService;
import com.erp.entitybuilder.web.v1.dto.EntityFieldDtos;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/v1/entities/{entityId}/fields")
public class EntityFieldsController {

    private final EntitySchemaService schemaService;
    private final ObjectMapper objectMapper;

    public EntityFieldsController(EntitySchemaService schemaService, ObjectMapper objectMapper) {
        this.schemaService = schemaService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('entity_builder:schema:write')")
    public EntityFieldDtos.EntityFieldDto create(
            @PathVariable UUID entityId,
            @Valid @RequestBody EntityFieldDtos.CreateFieldRequest req
    ) throws JsonProcessingException {
        UUID tenantId = SecurityUtil.principal().getTenantId();
        String configJson = req.getConfig() != null ? objectMapper.writeValueAsString(req.getConfig()) : null;
        EntityField f = schemaService.createField(
                tenantId,
                entityId,
                req.getName(),
                req.getSlug(),
                req.getFieldType(),
                req.isRequired(),
                req.isPii(),
                configJson
        );
        return toDto(f);
    }

    @GetMapping("/{fieldId}")
    @PreAuthorize("hasAuthority('entity_builder:schema:read')")
    public EntityFieldDtos.EntityFieldDto get(
            @PathVariable UUID entityId,
            @PathVariable UUID fieldId
    ) {
        UUID tenantId = SecurityUtil.principal().getTenantId();
        EntityField f = schemaService.getField(tenantId, entityId, fieldId);
        return toDto(f);
    }

    @PatchMapping("/{fieldId}")
    @PreAuthorize("hasAuthority('entity_builder:schema:write')")
    public EntityFieldDtos.EntityFieldDto update(
            @PathVariable UUID entityId,
            @PathVariable UUID fieldId,
            @Valid @RequestBody EntityFieldDtos.UpdateFieldRequest req
    ) throws JsonProcessingException {
        UUID tenantId = SecurityUtil.principal().getTenantId();
        String configJson = req.getConfig() != null ? objectMapper.writeValueAsString(req.getConfig()) : null;
        EntityField f = schemaService.updateField(
                tenantId,
                entityId,
                fieldId,
                Optional.ofNullable(req.getName()),
                Optional.ofNullable(req.getSlug()),
                Optional.ofNullable(req.getFieldType()),
                Optional.ofNullable(req.getRequired()),
                Optional.ofNullable(req.getPii()),
                Optional.ofNullable(configJson)
        );
        return toDto(f);
    }

    @DeleteMapping("/{fieldId}")
    @PreAuthorize("hasAuthority('entity_builder:schema:write')")
    public void delete(
            @PathVariable UUID entityId,
            @PathVariable UUID fieldId
    ) {
        UUID tenantId = SecurityUtil.principal().getTenantId();
        schemaService.deleteField(tenantId, entityId, fieldId);
    }

    private static EntityFieldDtos.EntityFieldDto toDto(EntityField f) {
        return new EntityFieldDtos.EntityFieldDto(
                f.getId(),
                f.getEntityId(),
                f.getName(),
                f.getSlug(),
                f.getFieldType(),
                f.isRequired(),
                f.isPii(),
                "ACTIVE",
                f.getCreatedAt(),
                f.getUpdatedAt()
        );
    }
}

