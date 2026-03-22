package com.erp.entitybuilder.web.v1;

import com.erp.entitybuilder.domain.EntityField;
import com.erp.entitybuilder.service.EntitySchemaService;
import com.erp.entitybuilder.web.v1.dto.EntityFieldDtos;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    @GetMapping
    @PreAuthorize("@entityBuilderSecurity.canReadSchema()")
    public List<EntityFieldDtos.EntityFieldDto> list(@PathVariable UUID entityId) {
        UUID tenantId = SecurityUtil.principal().getTenantId();
        return schemaService.listFields(tenantId, entityId).stream().map(this::toDto).toList();
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
                configJson,
                req.getSortOrder(),
                req.getLabelOverride(),
                req.getFormatString()
        );
        return toDto(f);
    }

    @GetMapping("/{fieldId}")
    @PreAuthorize("@entityBuilderSecurity.canReadSchema()")
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
        Optional<String> labelPatch = req.getLabelOverride() != null
                ? Optional.of(req.getLabelOverride())
                : Optional.empty();
        Optional<String> formatPatch = req.getFormatString() != null
                ? Optional.of(req.getFormatString())
                : Optional.empty();
        EntityField f = schemaService.updateField(
                tenantId,
                entityId,
                fieldId,
                Optional.ofNullable(req.getName()),
                Optional.ofNullable(req.getSlug()),
                Optional.ofNullable(req.getFieldType()),
                Optional.ofNullable(req.getRequired()),
                Optional.ofNullable(req.getPii()),
                Optional.ofNullable(configJson),
                Optional.ofNullable(req.getSortOrder()),
                labelPatch,
                formatPatch
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

    private EntityFieldDtos.EntityFieldDto toDto(EntityField f) {
        Map<String, Object> config = null;
        if (f.getConfig() != null && !f.getConfig().isBlank()) {
            try {
                config = objectMapper.readValue(f.getConfig(), new TypeReference<>() {});
            } catch (Exception ignored) {
                config = new HashMap<>();
            }
        }
        return new EntityFieldDtos.EntityFieldDto(
                f.getId(),
                f.getEntityId(),
                f.getName(),
                f.getSlug(),
                f.getFieldType(),
                f.isRequired(),
                f.isPii(),
                f.getSortOrder(),
                f.getLabelOverride(),
                f.getFormatString(),
                "ACTIVE",
                f.getCreatedAt(),
                f.getUpdatedAt(),
                config
        );
    }
}

