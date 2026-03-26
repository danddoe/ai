package com.erp.entitybuilder.service.catalog;

import com.erp.entitybuilder.catalog.EntityStatusCatalogConstants;
import com.erp.entitybuilder.config.PlatformTenantProperties;
import com.erp.entitybuilder.domain.DefinitionScope;
import com.erp.entitybuilder.domain.EntityDefinition;
import com.erp.entitybuilder.domain.EntityField;
import com.erp.entitybuilder.domain.EntityRecord;
import com.erp.entitybuilder.domain.EntityRecordValue;
import com.erp.entitybuilder.domain.EntityStatus;
import com.erp.entitybuilder.domain.EntityStatusTransition;
import com.erp.entitybuilder.domain.RecordScope;
import com.erp.entitybuilder.repository.EntityDefinitionRepository;
import com.erp.entitybuilder.repository.EntityFieldRepository;
import com.erp.entitybuilder.repository.EntityRecordRepository;
import com.erp.entitybuilder.repository.EntityRecordValueRepository;
import com.erp.entitybuilder.repository.EntityRelationshipRepository;
import com.erp.entitybuilder.repository.EntityStatusRepository;
import com.erp.entitybuilder.repository.EntityStatusTransitionRepository;
import com.erp.entitybuilder.service.EntitySchemaService;
import com.erp.entitybuilder.service.FieldTypes;
import com.erp.entitybuilder.service.RecordsService;
import com.erp.entitybuilder.service.RelationshipSchemaService;
import com.erp.entitybuilder.service.storage.FieldStorage;
import com.erp.entitybuilder.web.ApiException;
import jakarta.persistence.EntityManager;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Idempotently provisions entity_status schema, transitions, and STANDARD_RECORD seed rows for the platform tenant. */
@Service
public class EntityStatusDynamicEntityProvisioner {

    private final PlatformTenantProperties platformTenantProperties;
    private final EntitySchemaService schemaService;
    private final RelationshipSchemaService relationshipSchemaService;
    private final EntityDefinitionRepository entityRepository;
    private final EntityFieldRepository fieldRepository;
    private final EntityRelationshipRepository relationshipRepository;
    private final EntityStatusRepository entityStatusRepository;
    private final EntityStatusTransitionRepository transitionRepository;
    private final EntityRecordRepository recordRepository;
    private final EntityRecordValueRepository valueRepository;
    private final RecordsService recordsService;
    private final EntityManager entityManager;

    public EntityStatusDynamicEntityProvisioner(
            PlatformTenantProperties platformTenantProperties,
            EntitySchemaService schemaService,
            RelationshipSchemaService relationshipSchemaService,
            EntityDefinitionRepository entityRepository,
            EntityFieldRepository fieldRepository,
            EntityRelationshipRepository relationshipRepository,
            EntityStatusRepository entityStatusRepository,
            EntityStatusTransitionRepository transitionRepository,
            EntityRecordRepository recordRepository,
            EntityRecordValueRepository valueRepository,
            @Lazy RecordsService recordsService,
            EntityManager entityManager
    ) {
        this.platformTenantProperties = platformTenantProperties;
        this.schemaService = schemaService;
        this.relationshipSchemaService = relationshipSchemaService;
        this.entityRepository = entityRepository;
        this.fieldRepository = fieldRepository;
        this.relationshipRepository = relationshipRepository;
        this.entityStatusRepository = entityStatusRepository;
        this.transitionRepository = transitionRepository;
        this.recordRepository = recordRepository;
        this.valueRepository = valueRepository;
        this.recordsService = recordsService;
        this.entityManager = entityManager;
    }

    @Transactional
    public void ensureProvisioned() {
        if (!platformTenantProperties.isConfigured()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request",
                    "entitybuilder.platform.tenant-id is not configured");
        }
        UUID tid = platformTenantProperties.getTenantId();
        EntityDefinition entity = entityRepository.findByTenantIdAndSlug(tid, EntityStatusCatalogConstants.SLUG)
                .orElseGet(() -> schemaService.createEntity(
                        tid,
                        "Entity status",
                        EntityStatusCatalogConstants.SLUG,
                        "Workflow / lifecycle status definitions (platform)",
                        "ACTIVE",
                        "master_data",
                        false,
                        DefinitionScope.STANDARD_OBJECT
                ));
        schemaService.markEntityAsStandardCatalog(tid, entity.getId());

        ensureField(tid, entity.getId(), "Code", "code", "string", true, 10);
        ensureField(tid, entity.getId(), "Label", "label", "string", true, 20);
        ensureField(tid, entity.getId(), "Description", "description", "string", false, 30);
        ensureField(tid, entity.getId(), "Sort order", "sort_order", "number", true, 40);
        ensureField(tid, entity.getId(), "Category", "category", "string", false, 50);
        ensureField(tid, entity.getId(), "Is initial", "is_initial", "boolean", true, 60);
        ensureField(tid, entity.getId(), "Is terminal", "is_terminal", "boolean", true, 70);
        ensureField(tid, entity.getId(), "Is open", "is_open", "boolean", true, 80);
        ensureField(tid, entity.getId(), "Blocks edit", "blocks_edit", "boolean", true, 90);
        ensureField(tid, entity.getId(), "Blocks delete", "blocks_delete", "boolean", true, 100);
        ensureField(tid, entity.getId(), "Blocks post", "blocks_post", "boolean", true, 110);
        ensureField(tid, entity.getId(), "Is default", "is_default", "boolean", true, 120);
        ensureField(tid, entity.getId(), "Is system", "is_system", "boolean", true, 130);
        ensureField(tid, entity.getId(), "Is active", "is_active", "boolean", true, 140);
        ensureField(tid, entity.getId(), "Valid from", "valid_from", "date", false, 150);
        ensureField(tid, entity.getId(), "Valid to", "valid_to", "date", false, 160);
        ensureField(tid, entity.getId(), "Color", "color", "string", false, 170);
        ensureField(tid, entity.getId(), "Icon key", "icon_key", "string", false, 180);
        ensureField(tid, entity.getId(), "Metadata", "metadata", "string", false, 190);
        ensureReferenceField(tid, entity.getId(), "Parent status", "parent_status_id", false, 200);

        schemaService.updateEntity(
                tid,
                entity.getId(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                false,
                Optional.of("label"),
                false,
                Optional.empty(),
                Optional.empty()
        );

        if (relationshipRepository.findByTenantIdAndSlug(tid, EntityStatusCatalogConstants.RELATIONSHIP_PARENT_SLUG).isEmpty()) {
            relationshipSchemaService.create(
                    tid,
                    "Status parent",
                    EntityStatusCatalogConstants.RELATIONSHIP_PARENT_SLUG,
                    "one-to-many",
                    entity.getId(),
                    entity.getId(),
                    null,
                    "parent_status_id",
                    DefinitionScope.STANDARD_OBJECT
            );
        }

        seedStatus(tid, entity.getId(), "ACTIVE", "Active", "ACTIVE", true, false, true, false, false, false, true, true, true, 0);
        seedStatus(tid, entity.getId(), "INACTIVE", "Inactive", "INACTIVE", false, false, true, false, false, false, false, true, true, 10);
        seedStatus(tid, entity.getId(), "WIP", "Work in progress", "WIP", false, false, true, false, false, false, false, true, true, 15);
        seedStatus(tid, entity.getId(), "DELETED", "Deleted", "DONE", false, true, false, true, true, true, false, true, true, 20);

        seedTransitions(tid);
    }

    private void seedTransitions(UUID tid) {
        UUID active = deterministicStatusId(tid, "ACTIVE");
        UUID inactive = deterministicStatusId(tid, "INACTIVE");
        UUID wip = deterministicStatusId(tid, "WIP");
        UUID deleted = deterministicStatusId(tid, "DELETED");
        addTrans(tid, active, inactive);
        addTrans(tid, inactive, active);
        addTrans(tid, active, deleted);
        addTrans(tid, inactive, deleted);
        addTrans(tid, active, wip);
        addTrans(tid, wip, active);
        addTrans(tid, wip, inactive);
        addTrans(tid, inactive, wip);
        addTrans(tid, wip, deleted);
    }

    private void addTrans(UUID tid, UUID from, UUID to) {
        if (transitionRepository.existsByTenantIdAndFromStatusIdAndToStatusId(tid, from, to)) {
            return;
        }
        EntityStatusTransition t = new EntityStatusTransition();
        t.setTenantId(tid);
        t.setRecordScope(RecordScope.STANDARD_RECORD);
        t.setEntityDefinitionId(null);
        t.setFromStatusId(from);
        t.setToStatusId(to);
        t.setSortOrder(0);
        t.setRequiresComment(false);
        transitionRepository.save(t);
    }

    private void seedStatus(
            UUID tid,
            UUID entityDefId,
            String code,
            String label,
            String category,
            boolean initial,
            boolean terminal,
            boolean open,
            boolean blocksEdit,
            boolean blocksDelete,
            boolean blocksPost,
            boolean defaulted,
            boolean system,
            boolean active,
            int sortOrder
    ) {
        UUID id = deterministicStatusId(tid, code);
        EntityStatus row = entityStatusRepository.findById(id).orElse(new EntityStatus());
        row.setId(id);
        row.setTenantId(tid);
        row.setRecordScope(RecordScope.STANDARD_RECORD);
        row.setCode(code);
        row.setLabel(label);
        row.setDescription(null);
        row.setSortOrder(sortOrder);
        row.setCategory(category);
        row.setInitial(initial);
        row.setTerminal(terminal);
        row.setOpen(open);
        row.setBlocksEdit(blocksEdit);
        row.setBlocksDelete(blocksDelete);
        row.setBlocksPost(blocksPost);
        row.setDefaulted(defaulted);
        row.setSystem(system);
        row.setActive(active);
        entityStatusRepository.save(row);

        /*
         * EntityRecord keeps @GeneratedValue for normal API creates. Deterministic ids + save()/persist()
         * both fight Hibernate (merge/StaleState vs "detached entity passed to persist"). Insert new rows with SQL,
         * mutate managed instances when the row already exists.
         */
        Optional<EntityRecord> existingRec = recordRepository.findById(id);
        if (existingRec.isEmpty()) {
            entityManager.createNativeQuery(
                    "INSERT INTO entity_records (id, tenant_id, entity_id, external_id, status, record_scope, "
                            + "entity_status_id, search_vector, created_at, updated_at) "
                            + "VALUES (:id, :tenantId, :entityId, :externalId, 'ACTIVE', :recordScope, "
                            + ":entityStatusId, '', now(), now())"
            )
                    .setParameter("id", id)
                    .setParameter("tenantId", tid)
                    .setParameter("entityId", entityDefId)
                    .setParameter("externalId", code)
                    .setParameter("recordScope", RecordScope.STANDARD_RECORD.name())
                    .setParameter("entityStatusId", id)
                    .executeUpdate();
            entityManager.flush();
        } else {
            EntityRecord rec = existingRec.get();
            rec.setTenantId(tid);
            rec.setEntityId(entityDefId);
            rec.setExternalId(code);
            rec.setRecordScope(RecordScope.STANDARD_RECORD);
            rec.setEntityStatusId(id);
            rec.setStatus("ACTIVE");
        }

        List<EntityField> fields = fieldRepository.findByEntityIdOrderBySortOrderAscNameAsc(entityDefId);
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("code", code);
        values.put("label", label);
        values.put("description", null);
        values.put("sort_order", BigDecimal.valueOf(sortOrder));
        values.put("category", category);
        values.put("is_initial", initial);
        values.put("is_terminal", terminal);
        values.put("is_open", open);
        values.put("blocks_edit", blocksEdit);
        values.put("blocks_delete", blocksDelete);
        values.put("blocks_post", blocksPost);
        values.put("is_default", defaulted);
        values.put("is_system", system);
        values.put("is_active", active);
        values.put("valid_from", null);
        values.put("valid_to", null);
        values.put("color", null);
        values.put("icon_key", null);
        values.put("metadata", null);
        values.put("parent_status_id", null);

        for (EntityField f : fields) {
            if (FieldStorage.isCoreDomain(f)) {
                continue;
            }
            if (!values.containsKey(f.getSlug())) {
                continue;
            }
            Object raw = values.get(f.getSlug());
            EntityRecordValue rv = valueRepository.findByRecordIdAndFieldId(id, f.getId())
                    .orElseGet(() -> {
                        EntityRecordValue x = new EntityRecordValue();
                        x.setRecordId(id);
                        x.setFieldId(f.getId());
                        return x;
                    });
            applyMirrorValue(rv, f, raw);
            valueRepository.save(rv);
        }

        recordsService.reindexRecordForSearch(id, entityDefId);
    }

    private static void applyMirrorValue(EntityRecordValue rv, EntityField f, Object raw) {
        rv.setValueText(null);
        rv.setValueNumber(null);
        rv.setValueDate(null);
        rv.setValueBoolean(null);
        rv.setValueReference(null);
        if (raw == null) {
            return;
        }
        String ft = f.getFieldType();
        if (FieldTypes.isNumericFieldType(ft)) {
            rv.setValueNumber(raw instanceof BigDecimal b ? b : new BigDecimal(String.valueOf(raw)));
        } else if ("boolean".equalsIgnoreCase(FieldTypes.normalizeSqlFieldType(ft))) {
            rv.setValueBoolean(raw instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(raw)));
        } else if ("reference".equalsIgnoreCase(FieldTypes.normalizeSqlFieldType(ft))) {
            rv.setValueReference(raw instanceof UUID u ? u : UUID.fromString(String.valueOf(raw)));
        } else {
            rv.setValueText(String.valueOf(raw));
        }
    }

    private void ensureField(UUID tenantId, UUID entityId, String name, String slug, String type, boolean required, int sort) {
        if (fieldRepository.existsByEntityIdAndSlug(entityId, slug)) {
            return;
        }
        schemaService.createField(tenantId, entityId, name, slug, type, required, false,
                FieldStorage.configJson(Map.of("storage", FieldStorage.STORAGE_EAV_EXTENSION)), sort, null, null);
    }

    private void ensureReferenceField(UUID tenantId, UUID entityId, String name, String slug, boolean required, int sort) {
        if (fieldRepository.existsByEntityIdAndSlug(entityId, slug)) {
            return;
        }
        String cfg = FieldStorage.configJson(Map.of(
                "storage", FieldStorage.STORAGE_EAV_EXTENSION,
                "targetEntitySlug", EntityStatusCatalogConstants.SLUG
        ));
        schemaService.createField(tenantId, entityId, name, slug, "reference", required, false, cfg, sort, null, null);
    }

    public static UUID deterministicStatusId(UUID platformTenantId, String code) {
        String salt = platformTenantId + "|entity_status|" + code;
        return UUID.nameUUIDFromBytes(salt.getBytes(StandardCharsets.UTF_8));
    }
}
