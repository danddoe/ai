package com.erp.entitybuilder.service;

import com.erp.entitybuilder.domain.EntityDefinition;
import com.erp.entitybuilder.domain.EntityField;
import com.erp.entitybuilder.repository.EntityDefinitionRepository;
import com.erp.entitybuilder.repository.EntityFieldRepository;
import com.erp.entitybuilder.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class EntitySchemaService {

    private final EntityDefinitionRepository entityRepository;
    private final EntityFieldRepository fieldRepository;

    public EntitySchemaService(EntityDefinitionRepository entityRepository, EntityFieldRepository fieldRepository) {
        this.entityRepository = entityRepository;
        this.fieldRepository = fieldRepository;
    }

    @Transactional
    public EntityDefinition createEntity(UUID tenantId, String name, String slug, String description, String status) {
        if (entityRepository.existsByTenantIdAndSlug(tenantId, slug)) {
            throw new ApiException(HttpStatus.CONFLICT, "conflict", "Entity slug already exists", Map.of("slug", slug));
        }
        EntityDefinition e = new EntityDefinition();
        e.setTenantId(tenantId);
        e.setName(name);
        e.setSlug(slug);
        e.setDescription(description);
        if (status != null && !status.isBlank()) e.setStatus(status);
        return entityRepository.save(e);
    }

    @Transactional(readOnly = true)
    public List<EntityDefinition> listEntities(UUID tenantId) {
        return entityRepository.findAllByTenantIdOrderByNameAsc(tenantId);
    }

    @Transactional(readOnly = true)
    public EntityDefinition getEntity(UUID tenantId, UUID entityId) {
        return entityRepository.findById(entityId)
                .filter(e -> tenantId.equals(e.getTenantId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Entity not found"));
    }

    @Transactional
    public EntityDefinition updateEntity(UUID tenantId, UUID entityId, Optional<String> name, Optional<String> slug, Optional<String> description, Optional<String> status) {
        EntityDefinition e = getEntity(tenantId, entityId);

        name.filter(v -> !v.isBlank()).ifPresent(e::setName);
        slug.filter(v -> !v.isBlank()).ifPresent(newSlug -> {
            if (!newSlug.equals(e.getSlug()) && entityRepository.existsByTenantIdAndSlug(tenantId, newSlug)) {
                throw new ApiException(HttpStatus.CONFLICT, "conflict", "Entity slug already exists", Map.of("slug", newSlug));
            }
            e.setSlug(newSlug);
        });
        description.filter(v -> !v.isBlank()).ifPresent(e::setDescription);
        status.filter(v -> v != null && !v.isBlank()).ifPresent(e::setStatus);
        return entityRepository.save(e);
    }

    @Transactional
    public void deleteEntity(UUID tenantId, UUID entityId) {
        EntityDefinition e = getEntity(tenantId, entityId);
        entityRepository.delete(e);
    }

    // --- Tenant extensions (v1 implementation)
    // We model tenant extensions as tenant-owned entities that reference a base_entity_id in the `entities` table.

    @Transactional(readOnly = true)
    public List<EntityDefinition> listExtensions(UUID tenantId, UUID baseEntityId) {
        // base entity is global for this module, but must exist
        entityRepository.findById(baseEntityId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Base entity not found"));
        // In v1 we only allow listing by scanning; a dedicated query can be added later.
        return entityRepository.findAll().stream()
                .filter(e -> tenantId.equals(e.getTenantId()))
                .filter(e -> baseEntityId.equals(e.getBaseEntityId()))
                .toList();
    }

    @Transactional
    public EntityDefinition createExtension(UUID tenantId, UUID baseEntityId, String name, String slug, String description, String status) {
        // base entity must exist
        entityRepository.findById(baseEntityId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Base entity not found"));
        if (entityRepository.existsByTenantIdAndSlug(tenantId, slug)) {
            throw new ApiException(HttpStatus.CONFLICT, "conflict", "Extension slug already exists", Map.of("slug", slug));
        }
        EntityDefinition e = new EntityDefinition();
        e.setTenantId(tenantId);
        e.setBaseEntityId(baseEntityId);
        e.setName(name);
        e.setSlug(slug);
        e.setDescription(description);
        if (status != null && !status.isBlank()) e.setStatus(status);
        return entityRepository.save(e);
    }

    @Transactional
    public void deleteExtension(UUID tenantId, UUID extensionEntityId) {
        EntityDefinition e = getEntity(tenantId, extensionEntityId);
        if (e.getBaseEntityId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Not an extension entity");
        }
        entityRepository.delete(e);
    }

    @Transactional
    public EntityField createField(UUID tenantId, UUID entityId, String name, String slug, String fieldType, boolean required, boolean pii, String configJson) {
        // Ensure entity belongs to tenant
        getEntity(tenantId, entityId);

        if (fieldRepository.existsByEntityIdAndSlug(entityId, slug)) {
            throw new ApiException(HttpStatus.CONFLICT, "conflict", "Field slug already exists", Map.of("slug", slug));
        }

        EntityField f = new EntityField();
        f.setEntityId(entityId);
        f.setName(name);
        f.setSlug(slug);
        f.setFieldType(fieldType);
        f.setRequired(required);
        f.setPii(pii);
        f.setConfig(configJson);
        return fieldRepository.save(f);
    }

    @Transactional(readOnly = true)
    public EntityField getField(UUID tenantId, UUID entityId, UUID fieldId) {
        EntityField f = fieldRepository.findById(fieldId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Field not found"));
        if (!entityId.equals(f.getEntityId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "not_found", "Field not found");
        }
        // Ensure entity belongs to tenant
        getEntity(tenantId, entityId);
        return f;
    }

    @Transactional(readOnly = true)
    public boolean entityFieldExists(UUID tenantId, UUID entityId, String slug) {
        getEntity(tenantId, entityId);
        return fieldRepository.existsByEntityIdAndSlug(entityId, slug);
    }

    @Transactional
    public EntityField updateField(UUID tenantId, UUID entityId, UUID fieldId, Optional<String> name, Optional<String> slug, Optional<String> fieldType, Optional<Boolean> required, Optional<Boolean> pii, Optional<String> configJson) {
        EntityField f = getField(tenantId, entityId, fieldId);

        name.filter(v -> v != null && !v.isBlank()).ifPresent(f::setName);
        slug.filter(v -> v != null && !v.isBlank()).ifPresent(newSlug -> {
            if (!newSlug.equals(f.getSlug()) && fieldRepository.existsByEntityIdAndSlug(entityId, newSlug)) {
                throw new ApiException(HttpStatus.CONFLICT, "conflict", "Field slug already exists", Map.of("slug", newSlug));
            }
            f.setSlug(newSlug);
        });
        fieldType.filter(v -> v != null && !v.isBlank()).ifPresent(f::setFieldType);
        required.ifPresent(f::setRequired);
        pii.ifPresent(f::setPii);
        configJson.ifPresent(f::setConfig);

        return fieldRepository.save(f);
    }

    @Transactional
    public void deleteField(UUID tenantId, UUID entityId, UUID fieldId) {
        EntityField f = getField(tenantId, entityId, fieldId);
        fieldRepository.delete(f);
    }
}

