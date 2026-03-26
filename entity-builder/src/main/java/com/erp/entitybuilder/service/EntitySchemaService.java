package com.erp.entitybuilder.service;

import com.erp.entitybuilder.catalog.EntityStatusCatalogConstants;
import com.erp.entitybuilder.config.PlatformTenantProperties;
import com.erp.entitybuilder.domain.DefinitionScope;
import com.erp.entitybuilder.domain.EntityCategoryKeys;
import com.erp.entitybuilder.domain.EntityDefinition;
import com.erp.entitybuilder.domain.EntityField;
import com.erp.entitybuilder.domain.TenantEntityExtension;
import com.erp.entitybuilder.domain.TenantEntityExtensionField;
import com.erp.entitybuilder.repository.EntityDefinitionRepository;
import com.erp.entitybuilder.repository.EntityFieldRepository;
import com.erp.entitybuilder.repository.TenantEntityExtensionFieldRepository;
import com.erp.entitybuilder.repository.TenantEntityExtensionRepository;
import com.erp.entitybuilder.security.EntityBuilderSecurity;
import com.erp.entitybuilder.web.ApiException;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;

import java.util.ArrayList;
import java.util.Comparator;
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
    private final EntityBuilderSecurity entityBuilderSecurity;
    private final PlatformTenantProperties platformTenantProperties;
    private final EntityFieldLabelService entityFieldLabelService;

    public EntitySchemaService(
            EntityDefinitionRepository entityRepository,
            EntityFieldRepository fieldRepository,
            TenantEntityExtensionRepository tenantEntityExtensionRepository,
            TenantEntityExtensionFieldRepository tenantEntityExtensionFieldRepository,
            EntityBuilderSecurity entityBuilderSecurity,
            PlatformTenantProperties platformTenantProperties,
            @Lazy EntityFieldLabelService entityFieldLabelService
    ) {
        this.entityRepository = entityRepository;
        this.fieldRepository = fieldRepository;
        this.tenantEntityExtensionRepository = tenantEntityExtensionRepository;
        this.tenantEntityExtensionFieldRepository = tenantEntityExtensionFieldRepository;
        this.entityBuilderSecurity = entityBuilderSecurity;
        this.platformTenantProperties = platformTenantProperties;
        this.entityFieldLabelService = entityFieldLabelService;
    }

    private void assertCanMutateEntitySchema(EntityDefinition entity) {
        if (!entityBuilderSecurity.canMutateEntitySchema(entity)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "forbidden",
                    "Cannot modify platform catalog entity definitions with current permissions");
        }
    }

    /**
     * Creates a tenant entity. When {@code bootstrapDefaultFields} is true (portal/API create), adds a required EAV {@code name} field
     * and sets {@code defaultDisplayFieldSlug}. Catalog sync passes {@code false} so manifests own the field set.
     */
    @Transactional
    public EntityDefinition createEntity(UUID tenantId, String name, String slug, String description, String status, String categoryKey) {
        return createEntity(tenantId, name, slug, description, status, categoryKey, true, DefinitionScope.TENANT_OBJECT);
    }

    @Transactional
    public EntityDefinition createEntity(
            UUID tenantId,
            String name,
            String slug,
            String description,
            String status,
            String categoryKey,
            boolean bootstrapDefaultFields
    ) {
        return createEntity(tenantId, name, slug, description, status, categoryKey, bootstrapDefaultFields, DefinitionScope.TENANT_OBJECT);
    }

    @Transactional
    public EntityDefinition createEntity(
            UUID tenantId,
            String name,
            String slug,
            String description,
            String status,
            String categoryKey,
            boolean bootstrapDefaultFields,
            DefinitionScope definitionScope
    ) {
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
        e.setDefinitionScope(definitionScope);
        e = entityRepository.save(e);
        if (bootstrapDefaultFields) {
            createField(tenantId, e.getId(), "Name", "name", "string", true, false, null, 0, null, null);
            e.setDefaultDisplayFieldSlug("name");
            e = entityRepository.save(e);
        }
        return e;
    }

    /**
     * Marks an entity as platform catalog (system manifest) scope. Idempotent.
     */
    @Transactional
    public EntityDefinition markEntityAsStandardCatalog(UUID tenantId, UUID entityId) {
        EntityDefinition e = getEntity(tenantId, entityId);
        e.setDefinitionScope(DefinitionScope.STANDARD_OBJECT);
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
                return augmentEntityListWithPlatformEntityStatus(
                        tenantId, entityRepository.findAllByTenantIdOrderByNameAsc(tenantId), categoryKeyFilter, qNorm);
            }
            String key = EntityCategoryKeys.normalizeOrNull(categoryKeyFilter.get().trim());
            if (key == null) {
                return augmentEntityListWithPlatformEntityStatus(
                        tenantId, entityRepository.findAllByTenantIdOrderByNameAsc(tenantId), categoryKeyFilter, qNorm);
            }
            return augmentEntityListWithPlatformEntityStatus(
                    tenantId,
                    entityRepository.findAllByTenantIdAndCategoryKeyOrderByNameAsc(tenantId, key),
                    categoryKeyFilter,
                    qNorm
            );
        }
        Specification<EntityDefinition> spec = entitySearchSpec(tenantId, categoryKeyFilter, qNorm.get());
        List<EntityDefinition> searched = entityRepository.findAll(spec, PageRequest.of(0, ENTITY_SEARCH_MAX_RESULTS, Sort.by("name").ascending()))
                .getContent();
        return augmentEntityListWithPlatformEntityStatus(tenantId, searched, categoryKeyFilter, qNorm);
    }

    /**
     * Surfaces the platform {@link EntityStatusCatalogConstants#SLUG} definition in tenant entity lists so the portal
     * can open layouts/records while the user JWT tenant differs from {@link PlatformTenantProperties#getTenantId()}.
     */
    private List<EntityDefinition> augmentEntityListWithPlatformEntityStatus(
            UUID tenantId,
            List<EntityDefinition> fromTenant,
            Optional<String> categoryKeyFilter,
            Optional<String> qNorm
    ) {
        if (!platformTenantProperties.isConfigured()) {
            return fromTenant;
        }
        UUID platformTid = platformTenantProperties.getTenantId();
        if (tenantId.equals(platformTid)) {
            return fromTenant;
        }
        Optional<EntityDefinition> platformEntityOpt =
                entityRepository.findByTenantIdAndSlug(platformTid, EntityStatusCatalogConstants.SLUG);
        if (platformEntityOpt.isEmpty()) {
            return fromTenant;
        }
        EntityDefinition pe = platformEntityOpt.get();
        if (!DefinitionScope.STANDARD_OBJECT.equals(pe.getDefinitionScope())) {
            return fromTenant;
        }
        for (EntityDefinition e : fromTenant) {
            if (e.getId().equals(pe.getId()) || EntityStatusCatalogConstants.SLUG.equals(e.getSlug())) {
                return fromTenant;
            }
        }
        if (categoryKeyFilter.isPresent() && categoryKeyFilter.get() != null && !categoryKeyFilter.get().isBlank()) {
            String key = EntityCategoryKeys.normalizeOrNull(categoryKeyFilter.get().trim());
            if (key != null && !key.equals(pe.getCategoryKey())) {
                return fromTenant;
            }
        }
        if (qNorm.isPresent()) {
            String qLower = qNorm.get().toLowerCase(Locale.ROOT);
            boolean matches = pe.getName().toLowerCase(Locale.ROOT).contains(qLower)
                    || pe.getSlug().toLowerCase(Locale.ROOT).contains(qLower);
            try {
                if (UUID.fromString(qNorm.get().trim()).equals(pe.getId())) {
                    matches = true;
                }
            } catch (IllegalArgumentException ignored) {
                // not a UUID literal
            }
            if (!matches) {
                return fromTenant;
            }
        }
        List<EntityDefinition> merged = new ArrayList<>(fromTenant);
        merged.add(pe);
        merged.sort(Comparator.comparing(EntityDefinition::getName, String.CASE_INSENSITIVE_ORDER));
        if (qNorm.isPresent() && merged.size() > ENTITY_SEARCH_MAX_RESULTS) {
            return merged.subList(0, ENTITY_SEARCH_MAX_RESULTS);
        }
        return merged;
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

    /**
     * Entity belongs to {@code requestingTenantId}, or is the platform {@link EntityStatusCatalogConstants#SLUG}
     * standard definition (readable from any tenant).
     */
    @Transactional(readOnly = true)
    public EntityDefinition resolveEntityForTenantAccess(UUID requestingTenantId, UUID entityId) {
        EntityDefinition e = entityRepository.findById(entityId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Entity not found"));
        if (requestingTenantId.equals(e.getTenantId())) {
            return e;
        }
        if (platformTenantProperties.isConfigured()
                && platformTenantProperties.getTenantId().equals(e.getTenantId())
                && DefinitionScope.STANDARD_OBJECT.equals(e.getDefinitionScope())
                && EntityStatusCatalogConstants.SLUG.equals(e.getSlug())) {
            return e;
        }
        throw new ApiException(HttpStatus.NOT_FOUND, "not_found", "Entity not found");
    }

    @Transactional(readOnly = true)
    public EntityDefinition getEntityBySlug(UUID tenantId, String slug) {
        if (slug == null || slug.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "slug required");
        }
        String s = slug.trim();
        Optional<EntityDefinition> local = entityRepository.findByTenantIdAndSlug(tenantId, s);
        if (local.isPresent()) {
            return local.get();
        }
        if (platformTenantProperties.isConfigured()
                && EntityStatusCatalogConstants.SLUG.equals(s)) {
            return entityRepository.findByTenantIdAndSlug(platformTenantProperties.getTenantId(), s)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Entity not found", Map.of("slug", s)));
        }
        throw new ApiException(HttpStatus.NOT_FOUND, "not_found", "Entity not found", Map.of("slug", s));
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
            Optional<String> categoryKey,
            Optional<DefinitionScope> definitionScope
    ) {
        EntityDefinition e = getEntity(tenantId, entityId);
        assertCanMutateEntitySchema(e);

        definitionScope.ifPresent(newScope -> applyDefinitionScopeChange(e, newScope));

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

    private void applyDefinitionScopeChange(EntityDefinition e, DefinitionScope newScope) {
        if (newScope == null || newScope == e.getDefinitionScope()) {
            return;
        }
        if (!entityBuilderSecurity.canWriteFullSchema()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "forbidden",
                    "Platform schema write (entity_builder:schema:write) is required to change core vs tenant entity scope");
        }
        e.setDefinitionScope(newScope);
    }

    @Transactional
    public void deleteEntity(UUID tenantId, UUID entityId) {
        EntityDefinition e = getEntity(tenantId, entityId);
        assertCanMutateEntitySchema(e);
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
        e.setDefinitionScope(DefinitionScope.TENANT_OBJECT);
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
        assertCanMutateEntitySchema(entity);

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
        entityFieldLabelService.syncEnglishRowFromLegacyOverride(tenantId, entityId, f);
        return f;
    }

    @Transactional(readOnly = true)
    public List<EntityField> listFields(UUID tenantId, UUID entityId) {
        resolveEntityForTenantAccess(tenantId, entityId);
        return fieldRepository.findByEntityIdOrderBySortOrderAscNameAsc(entityId);
    }

    @Transactional(readOnly = true)
    public EntityField getField(UUID tenantId, UUID entityId, UUID fieldId) {
        EntityField f = fieldRepository.findById(fieldId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Field not found"));
        if (!entityId.equals(f.getEntityId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "not_found", "Field not found");
        }
        resolveEntityForTenantAccess(tenantId, entityId);
        return f;
    }

    @Transactional(readOnly = true)
    public boolean entityFieldExists(UUID tenantId, UUID entityId, String slug) {
        resolveEntityForTenantAccess(tenantId, entityId);
        return fieldRepository.existsByEntityIdAndSlug(entityId, slug);
    }

    @Transactional
    public EntityField updateField(UUID tenantId, UUID entityId, UUID fieldId, Optional<String> name, Optional<String> slug, Optional<String> fieldType, Optional<Boolean> required, Optional<Boolean> pii, Optional<String> configJson, Optional<Integer> sortOrder, Optional<String> labelOverride, Optional<String> formatString) {
        EntityDefinition entity = getEntity(tenantId, entityId);
        assertCanMutateEntitySchema(entity);
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
        formatString.ifPresent(s -> f.setFormatString(nullIfBlank(s)));

        fieldRepository.save(f);

        if (extId != null) {
            mirrorExtensionFieldUpdate(extId, oldSlug, f);
        }
        if (labelOverride.isPresent()) {
            if (labelOverride.get().isBlank()) {
                entityFieldLabelService.deleteSyncedEnglishWhenLegacyCleared(tenantId, entityId, fieldId);
            } else {
                entityFieldLabelService.syncEnglishRowFromLegacyOverride(tenantId, entityId, f);
            }
        }
        return f;
    }

    @Transactional
    public void deleteField(UUID tenantId, UUID entityId, UUID fieldId) {
        EntityDefinition entity = getEntity(tenantId, entityId);
        assertCanMutateEntitySchema(entity);
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

