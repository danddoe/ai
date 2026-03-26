package com.erp.entitybuilder.web.v1;

import com.erp.entitybuilder.domain.DefinitionScope;
import com.erp.entitybuilder.domain.EntityDefinition;
import com.erp.entitybuilder.domain.EntityField;
import com.erp.entitybuilder.service.EntityFieldLabelService;
import com.erp.entitybuilder.service.EntitySchemaService;
import com.erp.entitybuilder.web.RequestLocaleResolver;
import com.erp.entitybuilder.web.v1.dto.EntityFieldDtos;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/v1/entities/{entityId}/fields")
public class EntityFieldsController {

    private final EntitySchemaService schemaService;
    private final EntityFieldLabelService fieldLabelService;
    private final ObjectMapper objectMapper;

    public EntityFieldsController(
            EntitySchemaService schemaService,
            EntityFieldLabelService fieldLabelService,
            ObjectMapper objectMapper
    ) {
        this.schemaService = schemaService;
        this.fieldLabelService = fieldLabelService;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    @PreAuthorize("@entityBuilderSecurity.canReadSchema()")
    public List<EntityFieldDtos.EntityFieldDto> list(@PathVariable UUID entityId, HttpServletRequest request) {
        UUID tenantId = SecurityUtil.principal().getTenantId();
        DefinitionScope scope = schemaService.resolveEntityForTenantAccess(tenantId, entityId).getDefinitionScope();
        List<EntityField> fields = schemaService.listFields(tenantId, entityId);
        Map<UUID, Map<String, String>> labelMaps = fieldLabelService.labelsByFieldId(fields.stream().map(EntityField::getId).toList());
        return fields.stream().map(f -> toDto(f, scope, labelMaps, request)).toList();
    }

    @PostMapping
    @PreAuthorize("@entityBuilderSecurity.canWriteTenantSchema()")
    public EntityFieldDtos.EntityFieldDto create(
            @PathVariable UUID entityId,
            @Valid @RequestBody EntityFieldDtos.CreateFieldRequest req,
            HttpServletRequest request
    ) throws JsonProcessingException {
        UUID tenantId = SecurityUtil.principal().getTenantId();
        String configJson = req.getConfig() != null ? objectMapper.writeValueAsString(req.getConfig()) : null;
        EntityDefinition entity = schemaService.getEntity(tenantId, entityId);
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
        return toDto(f, entity.getDefinitionScope(), request);
    }

    @GetMapping("/{fieldId}")
    @PreAuthorize("@entityBuilderSecurity.canReadSchema()")
    public EntityFieldDtos.EntityFieldDto get(
            @PathVariable UUID entityId,
            @PathVariable UUID fieldId,
            HttpServletRequest request
    ) {
        UUID tenantId = SecurityUtil.principal().getTenantId();
        DefinitionScope scope = schemaService.resolveEntityForTenantAccess(tenantId, entityId).getDefinitionScope();
        EntityField f = schemaService.getField(tenantId, entityId, fieldId);
        return toDto(f, scope, request);
    }

    @PatchMapping("/{fieldId}")
    @PreAuthorize("@entityBuilderSecurity.canWriteTenantSchema()")
    public EntityFieldDtos.EntityFieldDto update(
            @PathVariable UUID entityId,
            @PathVariable UUID fieldId,
            @Valid @RequestBody EntityFieldDtos.UpdateFieldRequest req,
            HttpServletRequest request
    ) throws JsonProcessingException {
        UUID tenantId = SecurityUtil.principal().getTenantId();
        String configJson = req.getConfig() != null ? objectMapper.writeValueAsString(req.getConfig()) : null;
        Optional<String> labelPatch = req.getLabelOverride() != null
                ? Optional.of(req.getLabelOverride())
                : Optional.empty();
        Optional<String> formatPatch = req.getFormatString() != null
                ? Optional.of(req.getFormatString())
                : Optional.empty();
        DefinitionScope scope = schemaService.getEntity(tenantId, entityId).getDefinitionScope();
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
        return toDto(f, scope, request);
    }

    @PutMapping("/{fieldId}/labels/{locale}")
    @PreAuthorize("@entityBuilderSecurity.canWriteTenantSchema()")
    public EntityFieldDtos.EntityFieldDto putFieldLabel(
            @PathVariable UUID entityId,
            @PathVariable UUID fieldId,
            @PathVariable String locale,
            @RequestBody(required = false) EntityFieldDtos.UpsertFieldLabelRequest body,
            HttpServletRequest request
    ) {
        UUID tenantId = SecurityUtil.principal().getTenantId();
        String label = body != null ? body.label() : null;
        fieldLabelService.upsertLabel(tenantId, entityId, fieldId, locale, label);
        DefinitionScope scope = schemaService.getEntity(tenantId, entityId).getDefinitionScope();
        EntityField f = schemaService.getField(tenantId, entityId, fieldId);
        return toDto(f, scope, request);
    }

    @DeleteMapping("/{fieldId}")
    @PreAuthorize("@entityBuilderSecurity.canWriteTenantSchema()")
    public void delete(
            @PathVariable UUID entityId,
            @PathVariable UUID fieldId
    ) {
        UUID tenantId = SecurityUtil.principal().getTenantId();
        schemaService.deleteField(tenantId, entityId, fieldId);
    }

    private EntityFieldDtos.EntityFieldDto toDto(EntityField f, DefinitionScope definitionScope, HttpServletRequest request) {
        Map<UUID, Map<String, String>> labelMaps = fieldLabelService.labelsByFieldId(List.of(f.getId()));
        return toDto(f, definitionScope, labelMaps, request);
    }

    private EntityFieldDtos.EntityFieldDto toDto(
            EntityField f,
            DefinitionScope definitionScope,
            Map<UUID, Map<String, String>> labelMaps,
            HttpServletRequest request
    ) {
        Map<String, Object> config = null;
        if (f.getConfig() != null && !f.getConfig().isBlank()) {
            try {
                config = objectMapper.readValue(f.getConfig(), new TypeReference<>() {});
            } catch (Exception ignored) {
                config = new HashMap<>();
            }
        }
        Map<String, String> labels = new LinkedHashMap<>(labelMaps.getOrDefault(f.getId(), Map.of()));
        String lang = RequestLocaleResolver.resolveLanguage(request);
        String display = EntityFieldLabelService.resolveDisplayLabel(f, labels, lang);
        return new EntityFieldDtos.EntityFieldDto(
                f.getId(),
                f.getEntityId(),
                definitionScope,
                f.getName(),
                f.getSlug(),
                f.getFieldType(),
                f.isRequired(),
                f.isPii(),
                f.getSortOrder(),
                f.getLabelOverride(),
                display,
                labels,
                f.getFormatString(),
                "ACTIVE",
                f.getCreatedAt(),
                f.getUpdatedAt(),
                config
        );
    }
}
