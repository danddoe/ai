package com.erp.entitybuilder.service;

import com.erp.entitybuilder.domain.RecordListView;
import com.erp.entitybuilder.repository.EntityDefinitionRepository;
import com.erp.entitybuilder.repository.EntityFieldRepository;
import com.erp.entitybuilder.repository.RecordListViewRepository;
import com.erp.entitybuilder.web.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class RecordListViewService {

    private final EntityDefinitionRepository entityRepository;
    private final EntityFieldRepository fieldRepository;
    private final RecordListViewRepository recordListViewRepository;
    private final RecordListViewJsonValidator definitionValidator;
    private final ObjectMapper objectMapper;

    public RecordListViewService(
            EntityDefinitionRepository entityRepository,
            EntityFieldRepository fieldRepository,
            RecordListViewRepository recordListViewRepository,
            RecordListViewJsonValidator definitionValidator,
            ObjectMapper objectMapper
    ) {
        this.entityRepository = entityRepository;
        this.fieldRepository = fieldRepository;
        this.recordListViewRepository = recordListViewRepository;
        this.definitionValidator = definitionValidator;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<RecordListView> list(UUID tenantId, UUID entityId) {
        requireEntityInTenant(tenantId, entityId);
        return recordListViewRepository.findByTenantIdAndEntityId(tenantId, entityId);
    }

    @Transactional(readOnly = true)
    public RecordListView get(UUID tenantId, UUID viewId) {
        return recordListViewRepository.findById(viewId)
                .filter(v -> tenantId.equals(v.getTenantId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Record list view not found"));
    }

    @Transactional(readOnly = true)
    public RecordListView getForEntity(UUID tenantId, UUID entityId, UUID viewId) {
        RecordListView v = get(tenantId, viewId);
        if (!entityId.equals(v.getEntityId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "not_found", "Record list view not found");
        }
        return v;
    }

    @Transactional
    public RecordListView create(UUID tenantId, UUID entityId, String name, String definitionJson, boolean isDefault, String statusRaw) {
        requireEntityInTenant(tenantId, entityId);
        if (recordListViewRepository.findByTenantIdAndEntityIdAndName(tenantId, entityId, name).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "conflict", "Record list view name already exists", Map.of("name", name));
        }
        definitionValidator.validateOrThrow(definitionJson);
        validateFieldSlugsForEntity(entityId, definitionJson);

        RecordListView v = new RecordListView();
        v.setTenantId(tenantId);
        v.setEntityId(entityId);
        v.setName(name);
        v.setDefinition(definitionJson);
        v.setDefault(isDefault);
        v.setStatus(normalizeListViewStatus(statusRaw));

        if (isDefault) {
            clearOtherDefaults(tenantId, entityId, null);
        }
        return recordListViewRepository.save(v);
    }

    @Transactional
    public RecordListView update(
            UUID tenantId,
            UUID viewId,
            Optional<String> name,
            Optional<String> definitionJson,
            Optional<Boolean> isDefault,
            Optional<String> status
    ) {
        RecordListView v = get(tenantId, viewId);

        name.filter(s -> s != null && !s.isBlank()).ifPresent(newName -> {
            if (!newName.equals(v.getName())
                    && recordListViewRepository.findByTenantIdAndEntityIdAndName(tenantId, v.getEntityId(), newName).isPresent()) {
                throw new ApiException(HttpStatus.CONFLICT, "conflict", "Record list view name already exists", Map.of("name", newName));
            }
            v.setName(newName);
        });

        definitionJson.ifPresent(json -> {
            definitionValidator.validateOrThrow(json);
            validateFieldSlugsForEntity(v.getEntityId(), json);
            v.setDefinition(json);
        });

        status.filter(s -> s != null && !s.isBlank()).ifPresent(s -> v.setStatus(normalizeListViewStatus(s)));

        if (isDefault.isPresent()) {
            boolean newDefault = isDefault.get();
            v.setDefault(newDefault);
            if (newDefault) {
                clearOtherDefaults(tenantId, v.getEntityId(), v.getId());
            }
        }
        return recordListViewRepository.save(v);
    }

    @Transactional
    public void delete(UUID tenantId, UUID viewId) {
        RecordListView v = get(tenantId, viewId);
        recordListViewRepository.delete(v);
    }

    private static String normalizeListViewStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return "ACTIVE";
        }
        String s = raw.trim().toUpperCase(Locale.ROOT);
        if ("ACTIVE".equals(s) || "WIP".equals(s)) {
            return s;
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "status must be ACTIVE or WIP");
    }

    private void requireEntityInTenant(UUID tenantId, UUID entityId) {
        entityRepository.findById(entityId)
                .filter(e -> tenantId.equals(e.getTenantId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Entity not found"));
    }

    private void clearOtherDefaults(UUID tenantId, UUID entityId, UUID exceptId) {
        List<RecordListView> existing = recordListViewRepository.findByTenantIdAndEntityId(tenantId, entityId);
        for (RecordListView ex : existing) {
            if (exceptId != null && ex.getId().equals(exceptId)) {
                continue;
            }
            if (ex.isDefault()) {
                ex.setDefault(false);
                recordListViewRepository.save(ex);
            }
        }
    }

    private void validateFieldSlugsForEntity(UUID entityId, String definitionJson) {
        JsonNode root;
        try {
            root = objectMapper.readTree(definitionJson);
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Definition is not valid JSON");
        }
        JsonNode columns = root.get("columns");
        if (columns == null || !columns.isArray()) {
            return;
        }
        Set<String> wanted = new HashSet<>();
        for (JsonNode col : columns) {
            JsonNode slug = col.get("fieldSlug");
            if (slug != null && slug.isTextual()) {
                wanted.add(slug.asText().trim());
            }
        }
        if (wanted.isEmpty()) {
            return;
        }
        Set<String> have = new HashSet<>();
        fieldRepository.findByEntityId(entityId).forEach(f -> have.add(f.getSlug()));
        for (String s : wanted) {
            if (!have.contains(s)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Unknown field slug for this entity", Map.of("fieldSlug", s));
            }
        }
    }
}
