package com.erp.entitybuilder.service;

import com.erp.entitybuilder.domain.EntityCategoryKeys;
import com.erp.entitybuilder.domain.EntityDefinition;
import com.erp.entitybuilder.domain.EntityField;
import com.erp.entitybuilder.domain.TenantEntityExtension;
import com.erp.entitybuilder.domain.TenantEntityExtensionField;
import com.erp.entitybuilder.repository.EntityDefinitionRepository;
import com.erp.entitybuilder.repository.EntityFieldRepository;
import com.erp.entitybuilder.repository.TenantEntityExtensionFieldRepository;
import com.erp.entitybuilder.repository.TenantEntityExtensionRepository;
import com.erp.entitybuilder.web.ApiException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class EntitySchemaService {

    private final EntityDefinitionRepository entityRepository;
    private final EntityFieldRepository fieldRepository;
    private final TenantEntityExtensionRepository tenantEntityExtensionRepository;
    private final TenantEntityExtensionFieldRepository tenantEntityExtensionFieldRepository;

    public EntitySchemaService(
            EntityDefinitionRepository entityRepository,
            EntityFieldRepository fieldRepository,
            TenantEntityExtensionRepository tenantEntityExtensionRepository,
            TenantEntityExtensionFieldRepository tenantEntityExtensionFieldRepository
    ) {
        this.entityRepository = entityRepository;
        this.fieldRepository = fieldRepository;
        this.tenantEntityExtensionRepository = tenantEntityExtensionRepository;
        this.tenantEntityExtensionFieldRepository = tenantEntityExtensionFieldRepository;
    }

    @Transactional
    public EntityDefinition createEntity(UUID tenantId, String name, String slug, String description, String status, String categoryKey) {
        if (entityRepository.existsByTenantIdAndSlug(tenantId, slug)
                || tenantEntityExtensionRepository.existsByTenantIdAndSlug(tenantId, slug)) {
            throw new ApiException(HttpStatus.CONFLICT, "conflict", "Entity slug already exists", Map.of("slug", slug));
        }
        EntityDefinition e = new EntityDefinition();
        e.setTenantId(tenantId);
        e.setName(name);
        e.setSlug(slug);
        e.setDescription(description);
        if (status != null && !status.isBlank()) e.setStatus(status);
        e.setCategoryKey(EntityCategoryKeys.normalizeOrNull(categoryKey));
        return entityRepository.save(e);
    }

    public static final int ENTITY_SEARCH_Q_MAX_LEN = 100;
    public static final int ENTITY_SEARCH_MAX_RESULTS = 100;

    @Transactional(readOnly = true)
    public List<EntityDefinition> listEntities(UUID tenantId, Optional<String> categoryKeyFilter) {
        return listEntities(tenantId, categoryKeyFilter, Optional.empty());
    }

    /**
     * Lists entities for the tenant. When {@code q} is non-blank, matches name/slug (case-insensitive contains) or exact id;
     * results are capped at {@link #ENTITY_SEARCH_MAX_RESULTS} and ordered by name.
     */
    @Transactional(readOnly = true)
    public List<EntityDefinition> listEntities(UUID tenantId, Optional<String> categoryKeyFilter, Optional<String> qFilter) {
        Optional<String> qNorm = qFilter.map(String::trim).filter(s -> !s.isEmpty());
        if (qNorm.isPresent() && qNorm.get().length() > ENTITY_SEARCH_Q_MAX_LEN) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request",
                    "q must be at most " + ENTITY_SEARCH_Q_MAX_LEN + " characters");
        }
        if (qNorm.isEmpty()) {
            if (categoryKeyFilter.isEmpty() || categoryKeyFilter.get() == null || categoryKeyFilter.get().isBlank()) {
                return entityRepository.findAllByTenantIdOrderByNameAsc(tenantId);
            }
            String key = EntityCategoryKeys.normalizeOrNull(categoryKeyFilter.get().trim());
            if (key == null) {
                return entityRepository.findAllByTenantIdOrderByNameAsc(tenantId);
            }
            return entityRepository.findAllByTenantIdAndCategoryKeyOrderByNameAsc(tenantId, key);
        }
        Specification<EntityDefinition> spec = entitySearchSpec(tenantId, categoryKeyFilter, qNorm.get());
        return entityRepository.findAll(spec, PageRequest.of(0, ENTITY_SEARCH_MAX_RESULTS, Sort.by("name").ascending()))
                .getContent();
    }

    private static Specification<EntityDefinition> entitySearchSpec(
            UUID tenantId,
            Optional<String> categoryKeyFilter,
            String q
    ) {
        return (root, query, cb) -> {
            List<Predicate> ands = new ArrayList<>();
            ands.add(cb.equal(root.get("tenantId"), tenantId));

            if (categoryKeyFilter.isPresent() && categoryKeyFilter.get() != null && !categoryKeyFilter.get().isBlank()) {
                String key = EntityCategoryKeys.normalizeOrNull(categoryKeyFilter.get().trim());
                if (key != null) {
                    ands.add(cb.equal(root.get("categoryKey"), key));
                }
            }

            String term = "%" + q.toLowerCase(Locale.ROOT) + "%";
            List<Predicate> ors = new ArrayList<>();
            ors.add(cb.like(cb.lower(root.get("name")), term));
            ors.add(cb.like(cb.lower(root.get("slug")), term));
            try {
                UUID id = UUID.fromString(q.trim());
                ors.add(cb.equal(root.get("id"), id));
            } catch (IllegalArgumentException ignored) {
                // not a UUID literal
            }
            ands.add(cb.or(ors.toArray(Predicate[]::new)));
            return cb.and(ands.toArray(Predicate[]::new));
        };
    }

    @Transactional(readOnly = true)
    public EntityDefinition getEntity(UUID tenantId, UUID entityId) {
        return entityRepository.findById(entityId)
                .filter(e -> tenantId.equals(e.getTenantId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Entity not found"));
    }

    @Transactional(readOnly = true)
    public EntityDefinition getEntityBySlug(UUID tenantId, String slug) {
        if (slug == null || slug.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "slug required");
        }
        return entityRepository.findByTenantIdAndSlug(tenantId, slug.trim())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Entity not found", Map.of("slug", slug.trim())));
    }

    @Transactional
    public EntityDefinition updateEntity(
            UUID tenantId,
            UUID entityId,
            Optional<String> name,
            Optional<String> slug,
            Optional<String> description,
            Optional<String> status,
            boolean clearDefaultDisplayField,
            Optional<String> defaultDisplayFieldSlug,
            boolean clearCategoryKey,
            Optional<String> categoryKey
    ) {
        EntityDefinition e = getEntity(tenantId, entityId);

        name.filter(v -> !v.isBlank()).ifPresent(e::setName);
        slug.filter(v -> !v.isBlank()).ifPresent(newSlug -> {
            if (!newSlug.equals(e.getSlug()) && entityRepository.existsByTenantIdAndSlug(tenantId, newSlug)) {
                throw new ApiException(HttpStatus.CONFLICT, "conflict", "Entity slug already exists", Map.of("slug", newSlug));
            }
            if (!newSlug.equals(e.getSlug()) && tenantEntityExtensionRepository.existsByTenantIdAndSlug(tenantId, newSlug)) {
                throw new ApiException(HttpStatus.CONFLICT, "conflict", "Extension slug already exists", Map.of("slug", newSlug));
            }
            e.setSlug(newSlug);
        });
        description.filter(v -> !v.isBlank()).ifPresent(e::setDescription);
        status.filter(v -> v != null && !v.isBlank()).ifPresent(e::setStatus);

        if (clearDefaultDisplayField) {
            e.setDefaultDisplayFieldSlug(null);
        } else if (defaultDisplayFieldSlug.isPresent()) {
            String s = defaultDisplayFieldSlug.get();
            if (s == null || s.isBlank()) {
                e.setDefaultDisplayFieldSlug(null);
            } else {
                String trimmed = s.trim();
                if (!fieldRepository.existsByEntityIdAndSlug(entityId, trimmed)) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "defaultDisplayFieldSlug must match an existing field slug", Map.of("slug", trimmed));
                }
                e.setDefaultDisplayFieldSlug(trimmed);
            }
        }

        if (clearCategoryKey) {
            e.setCategoryKey(null);
        } else if (categoryKey.isPresent()) {
            String ck = categoryKey.get();
            if (ck == null || ck.isBlank()) {
                e.setCategoryKey(null);
            } else {
                e.setCategoryKey(EntityCategoryKeys.normalizeOrNull(ck));
            }
        }

        entityRepository.save(e);

        if (e.getTenantEntityExtensionId() != null) {
            TenantEntityExtension te = tenantEntityExtensionRepository.findById(e.getTenantEntityExtensionId())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Tenant extension not found"));
            name.filter(v -> !v.isBlank()).ifPresent(te::setName);
            slug.filter(v -> !v.isBlank()).ifPresent(newSlug -> {
                if (!newSlug.equals(te.getSlug()) && tenantEntityExtensionRepository.existsByTenantIdAndSlug(tenantId, newSlug)) {
                    throw new ApiException(HttpStatus.CONFLICT, "conflict", "Extension slug already exists", Map.of("slug", newSlug));
                }
                te.setSlug(newSlug);
            });
            status.filter(v -> v != null && !v.isBlank()).ifPresent(te::setStatus);
            tenantEntityExtensionRepository.save(te);
        }

        return e;
    }

    @Transactional
    public void deleteEntity(UUID tenantId, UUID entityId) {
        EntityDefinition e = getEntity(tenantId, entityId);
        if (e.getTenantEntityExtensionId() != null) {
            tenantEntityExtensionRepository.deleteById(e.getTenantEntityExtensionId());
            return;
        }
        entityRepository.delete(e);
    }

    // --- Tenant extensions: metadata in tenant_entity_extensions; synthetic row in entities for fields/records FKs.

    @Transactional(readOnly = true)
    public List<EntityDefinition> listExtensions(UUID tenantId, UUID baseEntityId) {
        entityRepository.findById(baseEntityId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Base entity not found"));

        List<EntityDefinition> fromMetadata = tenantEntityExtensionRepository
                .findByTenantIdAndBaseEntityIdOrderByNameAsc(tenantId, baseEntityId)
                .stream()
                .map(te -> entityRepository.findByTenantEntityExtensionId(te.getId()).orElse(null))
                .filter(e -> e != null)
                .toList();

        List<EntityDefinition> legacy = entityRepository.findByTenantIdAndBaseEntityIdAndTenantEntityExtensionIdIsNullOrderByNameAsc(
                tenantId, baseEntityId
        );

        if (legacy.isEmpty()) {
            return fromMetadata;
        }
        java.util.LinkedHashMap<UUID, EntityDefinition> merged = new java.util.LinkedHashMap<>();
        for (EntityDefinition e : fromMetadata) {
            merged.put(e.getId(), e);
        }
        for (EntityDefinition e : legacy) {
            merged.putIfAbsent(e.getId(), e);
        }
        return List.copyOf(merged.values());
    }

    @Transactional
    public EntityDefinition createExtension(UUID tenantId, UUID baseEntityId, String name, String slug, String description, String status) {
        EntityDefinition base = entityRepository.findById(baseEntityId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Base entity not found"));
        if (entityRepository.existsByTenantIdAndSlug(tenantId, slug)
                || tenantEntityExtensionRepository.existsByTenantIdAndSlug(tenantId, slug)) {
            throw new ApiException(HttpStatus.CONFLICT, "conflict", "Extension slug already exists", Map.of("slug", slug));
        }

        TenantEntityExtension te = new TenantEntityExtension();
        te.setTenantId(tenantId);
        te.setBaseEntityId(baseEntityId);
        te.setName(name);
        te.setSlug(slug);
        if (status != null && !status.isBlank()) {
            te.setStatus(status);
        }
        te = tenantEntityExtensionRepository.save(te);

        EntityDefinition e = new EntityDefinition();
        e.setTenantId(tenantId);
        e.setBaseEntityId(baseEntityId);
        e.setTenantEntityExtensionId(te.getId());
        e.setName(name);
        e.setSlug(slug);
        e.setDescription(description);
        if (status != null && !status.isBlank()) {
            e.setStatus(status);
        }
        e.setCategoryKey(base.getCategoryKey());
        return entityRepository.save(e);
    }

    @Transactional
    public void deleteExtension(UUID tenantId, UUID extensionEntityId) {
        EntityDefinition e = getEntity(tenantId, extensionEntityId);
        if (e.getBaseEntityId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Not an extension entity");
        }
        if (e.getTenantEntityExtensionId() != null) {
            tenantEntityExtensionRepository.deleteById(e.getTenantEntityExtensionId());
        } else {
            entityRepository.delete(e);
        }
    }

    @Transactional
    public EntityField createField(UUID tenantId, UUID entityId, String name, String slug, String fieldType, boolean required, boolean pii, String configJson, Integer sortOrder, String labelOverride, String formatString) {
        EntityDefinition entity = getEntity(tenantId, entityId);

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
        f.setSortOrder(sortOrder != null ? sortOrder : 0);
        f.setLabelOverride(nullIfBlank(labelOverride));
        f.setFormatString(nullIfBlank(formatString));
        fieldRepository.save(f);

        if (entity.getTenantEntityExtensionId() != null) {
            mirrorExtensionFieldCreate(entity.getTenantEntityExtensionId(), f);
        }
        return f;
    }

    @Transactional(readOnly = true)
    public List<EntityField> listFields(UUID tenantId, UUID entityId) {
        getEntity(tenantId, entityId);
        return fieldRepository.findByEntityIdOrderBySortOrderAscNameAsc(entityId);
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
    public EntityField updateField(UUID tenantId, UUID entityId, UUID fieldId, Optional<String> name, Optional<String> slug, Optional<String> fieldType, Optional<Boolean> required, Optional<Boolean> pii, Optional<String> configJson, Optional<Integer> sortOrder, Optional<String> labelOverride, Optional<String> formatString) {
        EntityDefinition entity = getEntity(tenantId, entityId);
        EntityField f = getField(tenantId, entityId, fieldId);
        String oldSlug = f.getSlug();
        UUID extId = entity.getTenantEntityExtensionId();

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
        sortOrder.ifPresent(f::setSortOrder);
        labelOverride.ifPresent(lo -> f.setLabelOverride(nullIfBlank(lo)));
        formatString.ifPresent(fs -> f.setFormatString(nullIfBlank(fs)));

        fieldRepository.save(f);

        if (extId != null) {
            mirrorExtensionFieldUpdate(extId, oldSlug, f);
        }
        return f;
    }

    @Transactional
    public void deleteField(UUID tenantId, UUID entityId, UUID fieldId) {
        EntityDefinition entity = getEntity(tenantId, entityId);
        EntityField f = getField(tenantId, entityId, fieldId);
        if (entity.getTenantEntityExtensionId() != null) {
            tenantEntityExtensionFieldRepository.deleteByTenantEntityExtensionIdAndSlug(
                    entity.getTenantEntityExtensionId(), f.getSlug());
        }
        fieldRepository.delete(f);
    }

    private void mirrorExtensionFieldCreate(UUID tenantEntityExtensionId, EntityField f) {
        if (tenantEntityExtensionFieldRepository.existsByTenantEntityExtensionIdAndSlug(tenantEntityExtensionId, f.getSlug())) {
            return;
        }
        TenantEntityExtensionField tef = new TenantEntityExtensionField();
        tef.setTenantEntityExtensionId(tenantEntityExtensionId);
        tef.setName(f.getName());
        tef.setSlug(f.getSlug());
        tef.setFieldType(f.getFieldType());
        tef.setRequired(f.isRequired());
        tef.setPii(f.isPii());
        tef.setConfig(f.getConfig());
        tef.setSortOrder(f.getSortOrder());
        tef.setLabelOverride(f.getLabelOverride());
        tef.setFormatString(f.getFormatString());
        tenantEntityExtensionFieldRepository.save(tef);
    }

    private void mirrorExtensionFieldUpdate(UUID tenantEntityExtensionId, String previousSlug, EntityField f) {
        tenantEntityExtensionFieldRepository.findByTenantEntityExtensionIdAndSlug(tenantEntityExtensionId, previousSlug)
                .ifPresentOrElse(
                        tef -> {
                            tef.setName(f.getName());
                            tef.setSlug(f.getSlug());
                            tef.setFieldType(f.getFieldType());
                            tef.setRequired(f.isRequired());
                            tef.setPii(f.isPii());
                            tef.setConfig(f.getConfig());
                            tef.setSortOrder(f.getSortOrder());
                            tef.setLabelOverride(f.getLabelOverride());
                            tef.setFormatString(f.getFormatString());
                            tenantEntityExtensionFieldRepository.save(tef);
                        },
                        () -> mirrorExtensionFieldCreate(tenantEntityExtensionId, f)
                );
    }

    private static String nullIfBlank(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s;
    }
}

