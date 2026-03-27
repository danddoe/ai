package com.erp.entitybuilder.service;

import com.erp.entitybuilder.domain.EntityField;
import com.erp.entitybuilder.domain.EntityFieldStatuses;
import com.erp.entitybuilder.domain.FormLayout;
import com.erp.entitybuilder.repository.EntityDefinitionRepository;
import com.erp.entitybuilder.repository.EntityFieldRepository;
import com.erp.entitybuilder.repository.FormLayoutRepository;
import com.erp.entitybuilder.web.ApiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class FormLayoutService {

    private final EntityDefinitionRepository entityRepository;
    private final EntityFieldRepository fieldRepository;
    private final FormLayoutRepository formLayoutRepository;
    private final FormLayoutTemplateLibrary templateLibrary;
    private final FormLayoutJsonValidator layoutJsonValidator;
    private final ObjectMapper objectMapper;

    public FormLayoutService(
            EntityDefinitionRepository entityRepository,
            EntityFieldRepository fieldRepository,
            FormLayoutRepository formLayoutRepository,
            FormLayoutTemplateLibrary templateLibrary,
            FormLayoutJsonValidator layoutJsonValidator,
            ObjectMapper objectMapper
    ) {
        this.entityRepository = entityRepository;
        this.fieldRepository = fieldRepository;
        this.formLayoutRepository = formLayoutRepository;
        this.templateLibrary = templateLibrary;
        this.layoutJsonValidator = layoutJsonValidator;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<FormLayout> list(UUID tenantId, UUID entityId) {
        // tenant isolation: entity must belong to tenant
        entityRepository.findById(entityId)
                .filter(e -> tenantId.equals(e.getTenantId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Entity not found"));
        return formLayoutRepository.findByTenantIdAndEntityId(tenantId, entityId);
    }

    @Transactional
    public FormLayout create(UUID tenantId, UUID entityId, String name, String layoutJson, boolean isDefault, String statusRaw) {
        entityRepository.findById(entityId)
                .filter(e -> tenantId.equals(e.getTenantId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Entity not found"));

        if (formLayoutRepository.findByTenantIdAndEntityIdAndName(tenantId, entityId, name).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "conflict", "Form layout name already exists", Map.of("name", name));
        }

        layoutJsonValidator.validateOrThrow(layoutJson, tenantId, entityId);

        FormLayout l = new FormLayout();
        l.setTenantId(tenantId);
        l.setEntityId(entityId);
        l.setName(name);
        l.setLayout(layoutJson);
        l.setDefault(isDefault);
        l.setStatus(normalizeFormLayoutStatus(statusRaw));

        if (isDefault) {
            // v1: best-effort default enforcement
            List<FormLayout> existing = formLayoutRepository.findByTenantIdAndEntityId(tenantId, entityId);
            for (FormLayout ex : existing) {
                if (ex.isDefault()) {
                    ex.setDefault(false);
                    formLayoutRepository.save(ex);
                }
            }
        }
        return formLayoutRepository.save(l);
    }

    @Transactional
    public FormLayout createFromTemplate(UUID tenantId, UUID entityId, String templateKey, String name, boolean isDefault) {
        String templateJson = templateLibrary.requireLayoutJson(templateKey);
        Map<String, UUID> slugToId = fieldRepository
                .findByEntityIdAndStatusOrderBySortOrderAscNameAsc(entityId, EntityFieldStatuses.ACTIVE)
                .stream()
                .collect(Collectors.toMap(EntityField::getSlug, EntityField::getId, (a, b) -> a));
        String mapped;
        try {
            mapped = applyFieldSlugMapping(templateJson, slugToId);
        } catch (JsonProcessingException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Could not process template layout");
        }
        return create(tenantId, entityId, name, mapped, isDefault, null);
    }

    @Transactional(readOnly = true)
    public FormLayout get(UUID tenantId, UUID layoutId) {
        return formLayoutRepository.findById(layoutId)
                .filter(l -> tenantId.equals(l.getTenantId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Form layout not found"));
    }

    @Transactional
    public FormLayout update(UUID tenantId, UUID layoutId, Optional<String> name, Optional<String> layoutJson, Optional<Boolean> isDefault, Optional<String> status) {
        FormLayout l = get(tenantId, layoutId);

        name.filter(v -> v != null && !v.isBlank()).ifPresent(l::setName);
        layoutJson.ifPresent(json -> {
            layoutJsonValidator.validateOrThrow(json, tenantId, l.getEntityId());
            l.setLayout(json);
        });
        status.filter(v -> v != null && !v.isBlank()).ifPresent(v -> l.setStatus(normalizeFormLayoutStatus(v)));

        if (isDefault.isPresent()) {
            boolean newDefault = isDefault.get();
            l.setDefault(newDefault);
            if (newDefault) {
                List<FormLayout> existing = formLayoutRepository.findByTenantIdAndEntityId(tenantId, l.getEntityId());
                for (FormLayout ex : existing) {
                    if (!ex.getId().equals(l.getId()) && ex.isDefault()) {
                        ex.setDefault(false);
                        formLayoutRepository.save(ex);
                    }
                }
            }
        }
        return formLayoutRepository.save(l);
    }

    @Transactional
    public void delete(UUID tenantId, UUID layoutId) {
        FormLayout l = get(tenantId, layoutId);
        formLayoutRepository.delete(l);
    }

    private static String normalizeFormLayoutStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return "ACTIVE";
        }
        String s = raw.trim().toUpperCase(Locale.ROOT);
        if ("ACTIVE".equals(s) || "WIP".equals(s)) {
            return s;
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "status must be ACTIVE or WIP");
    }

    private String applyFieldSlugMapping(String templateJson, Map<String, UUID> slugToId) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(templateJson);
        if (!root.isObject()) {
            return templateJson;
        }
        JsonNode versionNode = root.get("version");
        if (versionNode == null || !versionNode.isNumber() || versionNode.intValue() != 2) {
            return templateJson;
        }
        ObjectNode objectNode = (ObjectNode) root;
        JsonNode regions = objectNode.get("regions");
        if (regions != null && regions.isArray()) {
            for (JsonNode region : regions) {
                walkRows(region.get("rows"), slugToId);
            }
        }
        return objectMapper.writeValueAsString(objectNode);
    }

    private static void walkRows(JsonNode rows, Map<String, UUID> slugToId) {
        if (rows == null || !rows.isArray()) {
            return;
        }
        for (JsonNode row : rows) {
            JsonNode columns = row.get("columns");
            if (columns == null || !columns.isArray()) {
                continue;
            }
            for (JsonNode col : columns) {
                JsonNode items = col.get("items");
                if (items == null || !items.isArray()) {
                    continue;
                }
                for (JsonNode item : items) {
                    if (!item.isObject()) {
                        continue;
                    }
                    ObjectNode io = (ObjectNode) item;
                    JsonNode kindNode = io.get("kind");
                    if (kindNode != null && kindNode.isTextual() && "action".equals(kindNode.asText())) {
                        continue;
                    }
                    JsonNode slugNode = io.get("fieldSlug");
                    if (slugNode != null && slugNode.isTextual()) {
                        UUID fid = slugToId.get(slugNode.asText());
                        if (fid != null) {
                            io.put("fieldId", fid.toString());
                        } else {
                            io.putNull("fieldId");
                        }
                    }
                }
            }
        }
    }
}

