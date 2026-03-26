package com.erp.entitybuilder.service;

import com.erp.audit.AuditActions;
import com.erp.audit.AuditEvent;
import com.erp.audit.AuditLogWriter;
import com.erp.audit.AuditOperations;
import com.erp.audit.AuditResourceTypes;
import com.erp.entitybuilder.catalog.EntityStatusCatalogConstants;
import com.erp.entitybuilder.config.PlatformTenantProperties;
import com.erp.entitybuilder.domain.*;
import com.erp.entitybuilder.repository.*;
import com.erp.entitybuilder.security.EntityBuilderSecurity;
import com.erp.entitybuilder.service.query.RecordFilterQueryExecutor;
import com.erp.entitybuilder.service.query.RecordFilterValidator;
import com.erp.entitybuilder.service.query.ResolvedFilter;
import com.erp.entitybuilder.service.search.FieldSearchability;
import com.erp.entitybuilder.service.search.SearchLikeEscape;
import com.erp.entitybuilder.service.storage.FieldStorage;
import com.erp.entitybuilder.security.TenantPrincipal;
import com.erp.entitybuilder.web.ApiException;
import com.erp.entitybuilder.web.RequestLocaleResolver;
import com.erp.entitybuilder.web.v1.dto.RecordDtos;
import com.erp.entitybuilder.web.v1.dto.RecordQueryDtos;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

@Service
public class RecordsService {

    private static final int SEARCH_VECTOR_MAX_LEN = 8192;

    private static final String CREATE_RECORD_ROUTE_TEMPLATE = "/v1/tenants/{tenantId}/entities/{entityId}/records";
    private static final String POST_METHOD = "POST";
    private static final String AUDIT_SOURCE_SERVICE = "entity-builder";

    private final EntityDefinitionRepository entityRepository;
    private final EntityFieldRepository fieldRepository;
    private final EntityRecordRepository recordRepository;
    private final EntityRecordValueRepository valueRepository;
    private final PiiVaultRepository piiVaultRepository;
    private final RecordLinkRepository linkRepository;
    private final EntityRelationshipRepository relationshipRepository;
    private final IdempotencyRequestRepository idempotencyRepository;
    private final PiiCrypto piiCrypto;
    private final GlobalSearchIndexService globalSearchIndexService;
    private final RecordFilterValidator recordFilterValidator;
    private final RecordFilterQueryExecutor recordFilterQueryExecutor;
    private final AuditLogWriter auditLogWriter;
    private final DocumentNumberGenerationService documentNumberGenerationService;
    private final RecordUserLabelLookup recordUserLabelLookup;
    private final EntitySchemaService entitySchemaService;
    private final PlatformTenantProperties platformTenantProperties;
    private final EntityBuilderSecurity entityBuilderSecurity;
    private final EntityStatusLabelService entityStatusLabelService;
    private final EntityStatusAssignmentRepository entityStatusAssignmentRepository;

    private final ObjectMapper canonicalMapper;
    private final ObjectMapper responseMapper;

    public RecordsService(
            EntityDefinitionRepository entityRepository,
            EntityFieldRepository fieldRepository,
            EntityRecordRepository recordRepository,
            EntityRecordValueRepository valueRepository,
            PiiVaultRepository piiVaultRepository,
            RecordLinkRepository linkRepository,
            EntityRelationshipRepository relationshipRepository,
            IdempotencyRequestRepository idempotencyRepository,
            PiiCrypto piiCrypto,
            GlobalSearchIndexService globalSearchIndexService,
            RecordFilterValidator recordFilterValidator,
            RecordFilterQueryExecutor recordFilterQueryExecutor,
            DocumentNumberGenerationService documentNumberGenerationService,
            RecordUserLabelLookup recordUserLabelLookup,
            EntitySchemaService entitySchemaService,
            PlatformTenantProperties platformTenantProperties,
            EntityBuilderSecurity entityBuilderSecurity,
            EntityStatusLabelService entityStatusLabelService,
            EntityStatusAssignmentRepository entityStatusAssignmentRepository,
            @Autowired(required = false) AuditLogWriter auditLogWriter
    ) {
        this.entityRepository = entityRepository;
        this.fieldRepository = fieldRepository;
        this.recordRepository = recordRepository;
        this.valueRepository = valueRepository;
        this.piiVaultRepository = piiVaultRepository;
        this.linkRepository = linkRepository;
        this.relationshipRepository = relationshipRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.piiCrypto = piiCrypto;
        this.globalSearchIndexService = globalSearchIndexService;
        this.recordFilterValidator = recordFilterValidator;
        this.recordFilterQueryExecutor = recordFilterQueryExecutor;
        this.documentNumberGenerationService = documentNumberGenerationService;
        this.recordUserLabelLookup = recordUserLabelLookup;
        this.entitySchemaService = entitySchemaService;
        this.platformTenantProperties = platformTenantProperties;
        this.entityBuilderSecurity = entityBuilderSecurity;
        this.entityStatusLabelService = entityStatusLabelService;
        this.entityStatusAssignmentRepository = entityStatusAssignmentRepository;
        this.auditLogWriter = auditLogWriter;

        this.canonicalMapper = new ObjectMapper();
        this.canonicalMapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        this.responseMapper = new ObjectMapper();
    }

    @Transactional
    public RecordResponse createRecord(
            UUID tenantId,
            UUID userId,
            UUID entityId,
            String externalId,
            String businessDocumentNumber,
            Map<String, Object> values,
            List<LinkInput> links,
            String idempotencyKey,
            boolean piiReadPermission,
            UUID correlationId
    ) {
        Objects.requireNonNull(values, "values required");

        EntityDefinition entity = entitySchemaService.resolveEntityForTenantAccess(tenantId, entityId);

        List<EntityField> fields = fieldRepository.findByEntityId(entityId);
        Map<String, EntityField> fieldBySlug = new HashMap<>();
        for (EntityField f : fields) {
            fieldBySlug.put(f.getSlug(), f);
        }

        Map<String, Object> effectiveValues = new LinkedHashMap<>(values);
        applyOptimisticVersionDefaultOnCreate(effectiveValues, fields);
        values = effectiveValues;

        for (String providedSlug : values.keySet()) {
            if (!fieldBySlug.containsKey(providedSlug)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Unknown field slug", Map.of("field", providedSlug));
            }
            EntityField f = fieldBySlug.get(providedSlug);
            if (FieldStorage.isCoreDomain(f)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request",
                        "CORE_DOMAIN field must not appear in record payload; use the domain service API",
                        Map.of("field", providedSlug));
            }
        }

        for (EntityField f : fields) {
            if (!f.isRequired() || FieldStorage.isCoreDomain(f) || FieldTypes.isDocumentNumber(f)
                    || FieldTypes.isOptimisticVersionField(f)) {
                continue;
            }
            if (!values.containsKey(f.getSlug()) || values.get(f.getSlug()) == null) {
                throw missingRequiredFieldOnCreate(f);
            }
        }

        String requestHash = null;
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            requestHash = computeRequestHash(eavValuesOnly(values, fieldBySlug), links, externalId, businessDocumentNumber);
            Optional<IdempotencyRequest> existing = idempotencyRepository.findByTenantIdAndUserIdAndMethodAndRouteTemplateAndIdempotencyKey(
                    tenantId, userId, POST_METHOD, CREATE_RECORD_ROUTE_TEMPLATE, idempotencyKey
            );
            if (existing.isPresent()) {
                IdempotencyRequest ir = existing.get();
                if (ir.getExpiresAt().isBefore(Instant.now())) {
                    // expired: treat as no-op and proceed to create fresh below
                } else if (!Objects.equals(ir.getRequestHash(), requestHash)) {
                    throw new ApiException(HttpStatus.CONFLICT, "conflict", "Idempotency-Key reused with different request payload");
                } else {
                    return RecordResponse.fromJson(ir.getResponseJson(), piiReadPermission);
                }
            }
        }

        if (externalId != null && !externalId.isBlank()) {
            Optional<EntityRecord> existingRecord = recordRepository.findByTenantIdAndEntityIdAndExternalId(tenantId, entityId, externalId);
            if (existingRecord.isPresent()) {
                return toResponse(existingRecord.get().getId(), tenantId, piiReadPermission);
            }
        }

        String bdn = resolveBusinessDocumentNumberForCreate(businessDocumentNumber, values, fieldBySlug);
        if (bdn == null) {
            bdn = documentNumberGenerationService.generateIfAbsent(tenantId, entityId, fields).orElse(null);
        }
        if (bdn != null) {
            Optional<EntityRecord> existingByBdn = recordRepository.findByTenantIdAndEntityIdAndBusinessDocumentNumber(tenantId, entityId, bdn);
            if (existingByBdn.isPresent()) {
                return toResponse(existingByBdn.get().getId(), tenantId, piiReadPermission);
            }
        }

        // Create record row
        EntityRecord record = new EntityRecord();
        record.setTenantId(tenantId);
        record.setEntityId(entity.getId());
        record.setExternalId(externalId != null && !externalId.isBlank() ? externalId : null);
        record.setBusinessDocumentNumber(bdn);
        record.setCreatedBy(userId);
        record.setStatus("ACTIVE");
        record.setRecordScope(RecordScope.TENANT_RECORD);
        record = recordRepository.save(record);
        UUID recordId = record.getId();

        // Save EAV field values only (CORE_DOMAIN metadata fields have no persisted values here)
        for (EntityField f : fields) {
            if (FieldStorage.isCoreDomain(f) || FieldTypes.isDocumentNumber(f)) {
                continue;
            }
            Object rawValue = values.get(f.getSlug());
            UUID fieldId = f.getId();

            EntityRecordValue rv = valueRepository.findByRecordIdAndFieldId(recordId, fieldId)
                    .orElseGet(() -> {
                        EntityRecordValue x = new EntityRecordValue();
                        x.setRecordId(recordId);
                        x.setFieldId(fieldId);
                        return x;
                    });

            // Reset columns
            rv.setValueText(null);
            rv.setValueNumber(null);
            rv.setValueDate(null);
            rv.setValueBoolean(null);
            rv.setValueReference(null);

            if (rawValue == null) {
                valueRepository.save(rv);
                continue;
            }

            if (f.isPii()) {
                String plain = String.valueOf(rawValue);
                PiiCrypto.EncryptedValue enc = piiCrypto.encrypt(plain);
                rv.setValueText(null);
                rv.setValueNumber(null);
                rv.setValueDate(null);
                rv.setValueBoolean(null);
                rv.setValueReference(null);
                valueRepository.save(rv);

                PiiVaultEntry p = piiVaultRepository.findByTenantIdAndRecordIdAndFieldId(tenantId, recordId, fieldId)
                        .orElseGet(() -> {
                            PiiVaultEntry x = new PiiVaultEntry();
                            x.setTenantId(tenantId);
                            x.setRecordId(recordId);
                            x.setFieldId(fieldId);
                            return x;
                        });
                p.setEncryptedValue(enc.encryptedValueBase64());
                p.setKeyId(enc.keyId());
                piiVaultRepository.save(p);
            } else {
                applyTypedValue(rv, f.getFieldType(), rawValue);
                valueRepository.save(rv);
            }
        }

        // Save record links (if any)
        if (links != null) {
            for (LinkInput link : links) {
                upsertLinkStrict(tenantId, recordId, entityId, link, true);
            }
        }

        recomputeSearchVector(recordId, entity.getId());

        writeEntityRecordCreateAudit(tenantId, userId, correlationId, recordId, entity, values, links, fieldBySlug);

        // Build response
        RecordResponse response = toResponse(recordId, tenantId, piiReadPermission);

        // Persist idempotency entry after success
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            // if request_hash wasn't computed (shouldn't happen), compute it now.
            if (requestHash == null) {
                requestHash = computeRequestHash(eavValuesOnly(values, fieldBySlug), links, externalId, businessDocumentNumber);
            }
            String responseJson = safeSerialize(response.getRecordDto());
            IdempotencyRequest ir = new IdempotencyRequest();
            ir.setTenantId(tenantId);
            ir.setUserId(userId);
            ir.setMethod(POST_METHOD);
            ir.setRouteTemplate(CREATE_RECORD_ROUTE_TEMPLATE);
            ir.setIdempotencyKey(idempotencyKey);
            ir.setRequestHash(requestHash);
            ir.setStatusCode(201);
            ir.setResponseJson(responseJson);
            ir.setExpiresAt(Instant.now().plusSeconds(24 * 60 * 60L));
            try {
                idempotencyRepository.save(ir);
            } catch (DataIntegrityViolationException ignore) {
                // concurrent duplicate: safe to ignore, retry will hit existing
            }
        }

        return response;
    }

    @Transactional(readOnly = true)
    public RecordResponse getRecord(UUID tenantId, UUID recordId, boolean piiReadPermission) {
        EntityRecord record = recordRepository.findById(recordId)
                .filter(r -> recordVisibleToTenant(r, tenantId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Record not found"));

        return toResponse(record.getId(), tenantId, piiReadPermission);
    }

    @Transactional(readOnly = true)
    public RecordResponse getRecordByExternalId(UUID tenantId, UUID entityId, String externalId, boolean piiReadPermission) {
        if (externalId == null || externalId.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "externalId required");
        }
        EntityRecord record = findRecordByExternalId(tenantId, entityId, externalId.trim())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Record not found"));
        return toResponse(record.getId(), tenantId, piiReadPermission);
    }

    @Transactional(readOnly = true)
    public RecordResponse getRecordByBusinessDocumentNumber(UUID tenantId, UUID entityId, String businessDocumentNumber, boolean piiReadPermission) {
        if (businessDocumentNumber == null || businessDocumentNumber.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "businessDocumentNumber required");
        }
        String bdn = businessDocumentNumber.trim();
        Optional<EntityRecord> record = recordRepository.findByTenantIdAndEntityIdAndBusinessDocumentNumber(tenantId, entityId, bdn);
        if (record.isEmpty() && platformTenantProperties.isConfigured()) {
            record = recordRepository.findByTenantIdAndEntityIdAndBusinessDocumentNumber(platformTenantProperties.getTenantId(), entityId, bdn);
        }
        return toResponse(record.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Record not found")).getId(), tenantId, piiReadPermission);
    }

    @Transactional(readOnly = true)
    public PageResult listRecords(
            UUID tenantId,
            UUID entityId,
            int page,
            int pageSize,
            boolean piiReadPermission,
            UUID assignedForEntityId,
            UUID assignedForEntityFieldId
    ) {
        entitySchemaService.resolveEntityForTenantAccess(tenantId, entityId);
        // page is 1-based in the API layer
        int pageIndex = Math.max(0, page - 1);
        int size = clampPageSize(pageSize);
        var pageable = org.springframework.data.domain.PageRequest.of(pageIndex, size);

        List<UUID> assignedRecordIds = resolveAssignedStatusRecordIdsOrNull(
                tenantId, assignedForEntityId, assignedForEntityFieldId);
        List<UUID> listEntityIds = entityIdsForEntityStatusMirrorList(entityId);

        org.springframework.data.domain.Page<EntityRecord> p;
        if (assignedRecordIds != null) {
            if (platformTenantProperties.isConfigured()) {
                p = recordRepository.findVisibleByEntityIdInAndIdOrStatusIdIn(
                        listEntityIds, tenantId, platformTenantProperties.getTenantId(), assignedRecordIds, pageable);
            } else {
                p = recordRepository.findByTenantIdAndEntityIdInAndIdOrStatusIdIn(
                        tenantId, listEntityIds, assignedRecordIds, pageable);
            }
        } else if (platformTenantProperties.isConfigured()) {
            p = recordRepository.findVisibleByEntityIdIn(
                    listEntityIds, tenantId, platformTenantProperties.getTenantId(), pageable);
        } else {
            p = recordRepository.findByTenantIdAndEntityIdIn(tenantId, listEntityIds, pageable);
        }
        List<RecordDtos.RecordDto> items = p.getContent().stream()
                .map(r -> toResponse(r.getId(), tenantId, piiReadPermission).getRecordDto())
                .toList();
        return new PageResult(items, page, size, p.getTotalElements());
    }

    /**
     * Structured filter query over {@code entity_record_values}. Omit or null {@code request.filter} to list all records (same as GET list).
     */
    @Transactional(readOnly = true)
    public PageResult queryRecords(
            UUID tenantId,
            UUID entityId,
            RecordQueryDtos.RecordQueryRequest request,
            boolean piiReadPermission
    ) {
        entitySchemaService.resolveEntityForTenantAccess(tenantId, entityId);

        List<EntityField> fields = fieldRepository.findByEntityId(entityId);
        ResolvedFilter resolved = recordFilterValidator.validate(request.getFilter(), fields, piiReadPermission);

        int pageIndex = Math.max(0, request.getPage() - 1);
        int size = clampPageSize(request.getPageSize());
        long total = recordFilterQueryExecutor.countMatching(tenantId, entityId, resolved);
        RecordQueryDtos.RecordSort sort = request.getSort();
        String orderColumn = resolveRecordSortColumn(sort);
        boolean ascending = sort != null && "asc".equalsIgnoreCase(sort.getDirection());
        List<UUID> ids = recordFilterQueryExecutor.findRecordIds(
                tenantId, entityId, resolved, pageIndex * size, size, orderColumn, ascending);
        List<RecordDtos.RecordDto> items = ids.stream()
                .map(id -> toResponse(id, tenantId, piiReadPermission).getRecordDto())
                .toList();
        return new PageResult(items, request.getPage(), size, total);
    }

    private static String resolveRecordSortColumn(RecordQueryDtos.RecordSort sort) {
        if (sort == null || sort.getField() == null || sort.getField().isBlank()) {
            return "er.updated_at";
        }
        String f = sort.getField().trim().toLowerCase(Locale.ROOT);
        if ("record.updated_at".equals(f)) {
            return "er.updated_at";
        }
        if ("record.created_at".equals(f)) {
            return "er.created_at";
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Invalid sort field",
                Map.of("field", sort.getField(), "allowed", "record.updated_at, record.created_at"));
    }

    @Transactional
    public RecordResponse updateRecord(
            UUID tenantId,
            UUID userId,
            UUID entityId,
            UUID recordId,
            Map<String, Object> values,
            List<LinkInput> links,
            boolean piiReadPermission,
            UUID correlationId
    ) {
        EntityRecord record = recordRepository.findById(recordId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Record not found"));
        if (!record.getEntityId().equals(entityId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "not_found", "Record not found");
        }
        if (!recordVisibleToTenant(record, tenantId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "not_found", "Record not found");
        }
        if (record.getRecordScope() == RecordScope.STANDARD_RECORD && !entityBuilderSecurity.canWriteFullSchema()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "forbidden", "Cannot modify platform catalog records");
        }
        UUID valueTenantId = record.getTenantId();

        if (values == null) values = Map.of();
        List<EntityField> fields = fieldRepository.findByEntityId(entityId);
        Map<String, EntityField> fieldBySlug = new HashMap<>();
        for (EntityField f : fields) fieldBySlug.put(f.getSlug(), f);

        EntityField optimisticVersionField = null;
        for (EntityField f : fields) {
            if (FieldTypes.isOptimisticVersionField(f)) {
                optimisticVersionField = f;
                break;
            }
        }

        Map<String, Object> valuesToApply = new LinkedHashMap<>(values);
        BigDecimal versionAfterUpdate = null;
        if (optimisticVersionField != null) {
            BigDecimal currentVersion = readOptimisticVersion(valueRepository, recordId, optimisticVersionField.getId());
            String versionSlug = optimisticVersionField.getSlug();
            if (valuesToApply.containsKey(versionSlug)) {
                BigDecimal submitted = coerceToBigDecimal(valuesToApply.get(versionSlug));
                if (submitted.compareTo(currentVersion) != 0) {
                    throw new ApiException(HttpStatus.CONFLICT, "version_conflict",
                            "Record version mismatch (expected " + currentVersion.toPlainString() + ", got " + submitted.toPlainString() + ")",
                            Map.of("field", versionSlug, "currentVersion", currentVersion, "submittedVersion", submitted));
                }
                valuesToApply.remove(versionSlug);
            }
            versionAfterUpdate = currentVersion.add(BigDecimal.ONE);
        }

        Map<String, Object> auditBeforeValues = Map.of();
        List<Map<String, Object>> auditBeforeLinks = List.of();
        boolean willAuditLinks = links != null;
        boolean willAuditValues = !values.isEmpty();
        if (auditLogWriter != null && (willAuditValues || willAuditLinks)) {
            if (willAuditValues) {
                auditBeforeValues = collectAuditValuesForSlugs(recordId, valueTenantId, values.keySet(), fieldBySlug);
            }
            if (willAuditLinks) {
                auditBeforeLinks = auditLinkSnapshot(valueTenantId, recordId);
            }
        }

        for (String providedSlug : values.keySet()) {
            if (!fieldBySlug.containsKey(providedSlug)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Unknown field slug", Map.of("field", providedSlug));
            }
            EntityField cf = fieldBySlug.get(providedSlug);
            if (FieldStorage.isCoreDomain(cf)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request",
                        "CORE_DOMAIN field must not appear in record payload; use the domain service API",
                        Map.of("field", providedSlug));
            }
            if (FieldTypes.isDocumentNumber(cf)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request",
                        "document_number field is managed on the record; omit from values",
                        Map.of("field", providedSlug));
            }
            if (FieldTypes.isOptimisticVersionField(cf)) {
                continue;
            }
        }

        // Apply field updates (partial merge)
        for (var e : valuesToApply.entrySet()) {
            String slug = e.getKey();
            Object rawValue = e.getValue();
            EntityField f = fieldBySlug.get(slug);
            UUID fieldId = f.getId();

            EntityRecordValue rv = valueRepository.findByRecordIdAndFieldId(recordId, fieldId)
                    .orElseGet(() -> {
                        EntityRecordValue x = new EntityRecordValue();
                        x.setRecordId(recordId);
                        x.setFieldId(fieldId);
                        return x;
                    });

            rv.setValueText(null);
            rv.setValueNumber(null);
            rv.setValueDate(null);
            rv.setValueBoolean(null);
            rv.setValueReference(null);

            if (rawValue == null) {
                valueRepository.save(rv);
                if (f.isPii()) {
                    piiVaultRepository.findByTenantIdAndRecordIdAndFieldId(valueTenantId, recordId, fieldId)
                            .ifPresent(p -> piiVaultRepository.delete(p));
                }
                continue;
            }

            if (f.isPii()) {
                String plain = String.valueOf(rawValue);
                PiiCrypto.EncryptedValue enc = piiCrypto.encrypt(plain);

                PiiVaultEntry p = piiVaultRepository.findByTenantIdAndRecordIdAndFieldId(valueTenantId, recordId, fieldId)
                        .orElseGet(() -> {
                            PiiVaultEntry x = new PiiVaultEntry();
                            x.setTenantId(valueTenantId);
                            x.setRecordId(recordId);
                            x.setFieldId(fieldId);
                            return x;
                        });
                p.setEncryptedValue(enc.encryptedValueBase64());
                p.setKeyId(enc.keyId());
                piiVaultRepository.save(p);
                valueRepository.save(rv);
            } else {
                applyTypedValue(rv, f.getFieldType(), rawValue);
                valueRepository.save(rv);
            }
        }

        if (optimisticVersionField != null) {
            writeOptimisticVersionValue(recordId, optimisticVersionField.getId(), versionAfterUpdate);
        }

        // Enforce required non-null after merge (EAV fields only)
        for (EntityField f : fields) {
            if (!f.isRequired() || FieldStorage.isCoreDomain(f) || FieldTypes.isDocumentNumber(f)
                    || FieldTypes.isOptimisticVersionField(f)) {
                continue;
            }
            UUID fieldId = f.getId();
            if (f.isPii()) {
                boolean hasPii = piiVaultRepository.findByTenantIdAndRecordIdAndFieldId(tenantId, recordId, fieldId).isPresent();
                if (!hasPii) {
                    throw missingRequiredFieldAfterUpdate(
                            f,
                            "pii_value_missing",
                            "no PII vault entry after merge; required field cannot be empty"
                    );
                }
            } else {
                EntityRecordValue rv = valueRepository.findByRecordIdAndFieldId(recordId, fieldId)
                        .orElse(null);
                if (rv == null) {
                    throw missingRequiredFieldAfterUpdate(
                            f,
                            "value_row_missing",
                            "no entity_record_values row for this field after merge"
                    );
                }
                if (!isValueNonNull(f.getFieldType(), rv)) {
                    throw missingRequiredFieldAfterUpdate(
                            f,
                            "value_empty",
                            "stored value is null or empty for field type " + f.getFieldType()
                    );
                }
            }
        }

        // Links replace semantics
        if (links != null) {
            linkRepository.deleteByTenantIdAndFromRecordId(valueTenantId, recordId);
            for (LinkInput link : links) {
                upsertLinkStrict(tenantId, recordId, entityId, link, true);
            }
        }

        recomputeSearchVector(recordId, entityId);

        if (auditLogWriter != null && (willAuditValues || willAuditLinks)) {
            EntityDefinition entityDef = entityRepository.findById(entityId).orElse(null);
            if (entityDef != null) {
                writeEntityRecordUpdateAudit(
                        tenantId, userId, correlationId, recordId, entityDef, record,
                        values, links, fieldBySlug, auditBeforeValues, auditBeforeLinks
                );
            }
        }

        record.setUpdatedBy(userId);
        recordRepository.save(record);

        return toResponse(recordId, tenantId, piiReadPermission);
    }

    @Transactional(readOnly = true)
    public RecordDtos.RecordLookupResponse lookupRecords(
            UUID tenantId,
            UUID entityId,
            String term,
            int limit,
            List<String> displaySlugs,
            boolean piiReadPermission,
            UUID assignedForEntityId,
            UUID assignedForEntityFieldId
    ) {
        entitySchemaService.resolveEntityForTenantAccess(tenantId, entityId);

        if (term == null || term.isBlank() || term.trim().length() < 2) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "term must be at least 2 characters");
        }

        int lim = limit <= 0 ? 20 : Math.min(limit, 50);
        String pattern = "%" + SearchLikeEscape.escapeLikePattern(term.trim()) + "%";
        List<UUID> assignedRecordIds = resolveAssignedStatusRecordIdsOrNull(
                tenantId, assignedForEntityId, assignedForEntityFieldId);
        List<UUID> listEntityIds = entityIdsForEntityStatusMirrorList(entityId);
        List<UUID> ids;
        if (assignedRecordIds != null) {
            if (platformTenantProperties.isConfigured()) {
                ids = recordRepository.findIdsForSearchLookupVisibleAssigned(
                        tenantId, listEntityIds, pattern, lim, platformTenantProperties.getTenantId(), assignedRecordIds);
            } else {
                ids = recordRepository.findIdsForSearchLookupAssigned(tenantId, listEntityIds, pattern, lim, assignedRecordIds);
            }
        } else if (platformTenantProperties.isConfigured()) {
            ids = recordRepository.findIdsForSearchLookupVisible(
                    tenantId, listEntityIds, pattern, lim, platformTenantProperties.getTenantId());
        } else {
            ids = recordRepository.findIdsForSearchLookup(tenantId, listEntityIds, pattern, lim);
        }

        EntityDefinition entityDef = entityRepository.findById(entityId).orElseThrow();
        List<EntityField> fields = fieldRepository.findByEntityId(entityId);
        Map<String, EntityField> fieldBySlug = new LinkedHashMap<>();
        for (EntityField f : fields) {
            fieldBySlug.put(f.getSlug(), f);
        }

        LinkedHashSet<String> slugOrder = new LinkedHashSet<>();
        if (entityDef.getDefaultDisplayFieldSlug() != null && !entityDef.getDefaultDisplayFieldSlug().isBlank()) {
            slugOrder.add(entityDef.getDefaultDisplayFieldSlug());
        }
        if (displaySlugs != null) {
            for (String s : displaySlugs) {
                if (s != null && !s.isBlank() && slugOrder.size() < 12) {
                    slugOrder.add(s.trim());
                }
            }
        }

        List<RecordDtos.RecordLookupItemDto> items = new ArrayList<>();
        for (UUID rid : ids) {
            List<EntityRecordValue> valueRows = valueRepository.findByRecordId(rid);
            Map<UUID, EntityRecordValue> byFieldId = new HashMap<>();
            for (EntityRecordValue v : valueRows) {
                byFieldId.put(v.getFieldId(), v);
            }

            Map<String, Object> valueMap = new LinkedHashMap<>();
            for (String slug : slugOrder) {
                EntityField f = fieldBySlug.get(slug);
                if (f == null) {
                    continue;
                }
                EntityRecordValue v = byFieldId.get(f.getId());
                UUID rowTenant = recordRepository.findById(rid).map(EntityRecord::getTenantId).orElse(tenantId);
                valueMap.put(slug, decodeValue(f, v, rowTenant, rid, piiReadPermission));
            }

            UUID rowTenant = recordRepository.findById(rid).map(EntityRecord::getTenantId).orElse(tenantId);
            String displayLabel = resolveDisplayLabel(entityDef, fields, byFieldId, rowTenant, rid, piiReadPermission);
            items.add(new RecordDtos.RecordLookupItemDto(rid, displayLabel, valueMap));
        }

        return new RecordDtos.RecordLookupResponse(items);
    }

    /**
     * Resolves assigned status ids for list/lookup filtering. Field-scoped rows win when non-empty; otherwise
     * entity-definition scope; when both are empty or unavailable, {@code null} (no extra filter).
     */
    private List<UUID> resolveAssignedStatusRecordIdsOrNull(
            UUID tenantId,
            UUID assignedForEntityId,
            UUID assignedForEntityFieldId
    ) {
        if (assignedForEntityFieldId != null) {
            List<UUID> fieldIds = entityStatusAssignmentRepository.findEntityStatusIdsByTenantIdAndAssignmentScopeAndScopeId(
                    tenantId, AssignmentScope.ENTITY_FIELD, assignedForEntityFieldId);
            if (!fieldIds.isEmpty()) {
                return fieldIds;
            }
        }
        if (assignedForEntityId == null) {
            return null;
        }
        List<UUID> entIds = entityStatusAssignmentRepository.findEntityStatusIdsByTenantIdAndAssignmentScopeAndScopeId(
                tenantId, AssignmentScope.ENTITY_DEFINITION, assignedForEntityId);
        return entIds.isEmpty() ? null : entIds;
    }

    /**
     * Platform-seeded {@code entity_status} mirror rows use the platform tenant's entity definition id. Each tenant
     * also has its own {@code entities} row for slug {@code entity_status} (different primary key). Listing by the
     * request entity id alone would miss STANDARD_RECORD mirrors; include both ids when slug matches.
     */
    private List<UUID> entityIdsForEntityStatusMirrorList(UUID entityId) {
        EntityDefinition def = entityRepository.findById(entityId).orElse(null);
        if (def == null) {
            return List.of(entityId);
        }
        if (!EntityStatusCatalogConstants.SLUG.equals(def.getSlug())) {
            return List.of(entityId);
        }
        LinkedHashSet<UUID> ids = new LinkedHashSet<>();
        ids.add(entityId);
        if (platformTenantProperties.isConfigured()) {
            UUID pt = platformTenantProperties.getTenantId();
            entityRepository.findByTenantIdAndSlug(pt, EntityStatusCatalogConstants.SLUG)
                    .map(EntityDefinition::getId)
                    .ifPresent(ids::add);
        }
        return new ArrayList<>(ids);
    }

    @Transactional
    public void deleteRecord(UUID tenantId, UUID userId, UUID entityId, UUID recordId, UUID correlationId) {
        EntityRecord record = recordRepository.findById(recordId)
                .filter(r -> entityId.equals(r.getEntityId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Record not found"));
        if (!recordVisibleToTenant(record, tenantId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "not_found", "Record not found");
        }
        if (record.getRecordScope() == RecordScope.STANDARD_RECORD && !entityBuilderSecurity.canWriteFullSchema()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "forbidden", "Cannot delete platform catalog records");
        }
        EntityDefinition entityDef = entityRepository.findById(entityId).orElse(null);
        List<EntityField> fields = fieldRepository.findByEntityId(entityId);
        Map<String, EntityField> fieldBySlug = new HashMap<>();
        for (EntityField f : fields) {
            fieldBySlug.put(f.getSlug(), f);
        }
        Map<String, Object> snapshotValues = auditLogWriter != null && entityDef != null
                ? collectAuditValuesForSlugs(recordId, tenantId, fieldBySlug.keySet(), fieldBySlug)
                : Map.of();
        List<Map<String, Object>> snapshotLinks = auditLogWriter != null
                ? auditLinkSnapshot(tenantId, recordId)
                : List.of();

        globalSearchIndexService.deleteEntityRecord(record.getTenantId(), recordId);
        recordRepository.delete(record);

        if (auditLogWriter != null && entityDef != null) {
            writeEntityRecordDeleteAudit(tenantId, userId, correlationId, recordId, entityDef, record, snapshotValues, snapshotLinks);
        }
    }

    @Transactional(readOnly = true)
    public LinksResult listLinks(UUID tenantId, UUID fromRecordId) {
        EntityRecord from = recordRepository.findById(fromRecordId)
                .filter(r -> recordVisibleToTenant(r, tenantId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "From record not found"));
        UUID linkTenant = from.getTenantId();
        List<RecordLink> links = linkRepository.findByTenantIdAndFromRecordId(linkTenant, fromRecordId);
        Map<UUID, EntityRelationship> relationshipsById = new HashMap<>();
        for (EntityRelationship r : relationshipRepository.findByTenantId(linkTenant)) relationshipsById.put(r.getId(), r);

        List<com.erp.entitybuilder.web.v1.dto.RecordDtos.LinkDto> outLinks = new ArrayList<>();
        for (RecordLink l : links) {
            EntityRelationship rel = relationshipsById.get(l.getRelationshipId());
            outLinks.add(new com.erp.entitybuilder.web.v1.dto.RecordDtos.LinkDto(rel != null ? rel.getSlug() : null, l.getToRecordId()));
        }
        return new LinksResult(outLinks);
    }

    @Transactional
    public void addLink(UUID tenantId, UUID userId, UUID fromRecordId, String relationshipSlug, UUID toRecordId, UUID correlationId) {
        EntityRecord fromRecord = recordRepository.findById(fromRecordId)
                .filter(r -> recordVisibleToTenant(r, tenantId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "From record not found"));
        upsertLinkStrict(tenantId, fromRecordId, fromRecord.getEntityId(), new LinkInput(relationshipSlug, toRecordId), false);
        if (auditLogWriter != null) {
            EntityDefinition entityDef = entityRepository.findById(fromRecord.getEntityId()).orElse(null);
            if (entityDef != null) {
                Map<String, Object> linkPayload = new LinkedHashMap<>();
                linkPayload.put("relationshipSlug", relationshipSlug);
                linkPayload.put("toRecordId", toRecordId);
                AuditEvent.Builder b = AuditEvent.builder()
                        .tenantId(tenantId)
                        .actorId(userId)
                        .sourceService(AUDIT_SOURCE_SERVICE)
                        .operation(AuditOperations.UPDATE)
                        .action(AuditActions.ENTITY_RECORD_LINK_ADD)
                        .resourceType(AuditResourceTypes.ENTITY_RECORD)
                        .resourceId(fromRecordId)
                        .correlationId(correlationId)
                        .putPayload("context", auditContextMap(entityDef, fromRecord))
                        .putPayload("link", linkPayload);
                attachActorSnapshot(b);
                auditLogWriter.append(b.build());
            }
        }
    }

    @Transactional
    public void deleteLink(UUID tenantId, UUID userId, UUID fromRecordId, String relationshipSlug, UUID toRecordId, UUID correlationId) {
        EntityRecord fromRecord = recordRepository.findById(fromRecordId)
                .filter(r -> recordVisibleToTenant(r, tenantId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "From record not found"));
        UUID linkTenant = fromRecord.getTenantId();
        EntityRelationship rel = resolveRelationshipForLink(linkTenant, relationshipSlug);
        RecordLink l = linkRepository.findByTenantIdAndFromRecordIdAndRelationshipIdAndToRecordId(linkTenant, fromRecordId, rel.getId(), toRecordId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Link not found"));
        linkRepository.delete(l);
        if (auditLogWriter != null) {
            EntityDefinition entityDef = entityRepository.findById(fromRecord.getEntityId()).orElse(null);
            if (entityDef != null) {
                Map<String, Object> linkPayload = new LinkedHashMap<>();
                linkPayload.put("relationshipSlug", relationshipSlug);
                linkPayload.put("toRecordId", toRecordId);
                AuditEvent.Builder b = AuditEvent.builder()
                        .tenantId(tenantId)
                        .actorId(userId)
                        .sourceService(AUDIT_SOURCE_SERVICE)
                        .operation(AuditOperations.UPDATE)
                        .action(AuditActions.ENTITY_RECORD_LINK_DELETE)
                        .resourceType(AuditResourceTypes.ENTITY_RECORD)
                        .resourceId(fromRecordId)
                        .correlationId(correlationId)
                        .putPayload("context", auditContextMap(entityDef, fromRecord))
                        .putPayload("link", linkPayload);
                attachActorSnapshot(b);
                auditLogWriter.append(b.build());
            }
        }
    }

    @Transactional
    public void reindexRecordForSearch(UUID recordId, UUID entityId) {
        recomputeSearchVector(recordId, entityId);
    }

    private boolean recordVisibleToTenant(EntityRecord r, UUID requestingTenantId) {
        if (requestingTenantId.equals(r.getTenantId())) {
            return true;
        }
        return r.getRecordScope() == RecordScope.STANDARD_RECORD
                && platformTenantProperties.isConfigured()
                && platformTenantProperties.getTenantId().equals(r.getTenantId());
    }

    private Optional<EntityRecord> findRecordByExternalId(UUID tenantId, UUID entityId, String externalId) {
        Optional<EntityRecord> local = recordRepository.findByTenantIdAndEntityIdAndExternalId(tenantId, entityId, externalId);
        if (local.isPresent()) {
            return local;
        }
        if (platformTenantProperties.isConfigured()) {
            return recordRepository.findByTenantIdAndEntityIdAndExternalId(platformTenantProperties.getTenantId(), entityId, externalId);
        }
        return Optional.empty();
    }

    private RecordResponse toResponse(UUID recordId, UUID tenantId, boolean piiReadPermission) {
        EntityRecord record = recordRepository.findById(recordId)
                .filter(r -> recordVisibleToTenant(r, tenantId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Record not found"));

        UUID storageTenantId = record.getTenantId();
        List<EntityField> fields = fieldRepository.findByEntityId(record.getEntityId());
        // Load value rows
        List<EntityRecordValue> values = valueRepository.findByRecordId(recordId);
        Map<UUID, EntityRecordValue> byFieldId = new HashMap<>();
        for (EntityRecordValue v : values) byFieldId.put(v.getFieldId(), v);

        Map<String, Object> outValues = new LinkedHashMap<>();
        for (EntityField f : fields) {
            if (FieldStorage.isCoreDomain(f)) {
                continue;
            }
            if (FieldTypes.isDocumentNumber(f)) {
                outValues.put(f.getSlug(), record.getBusinessDocumentNumber());
                continue;
            }
            EntityRecordValue v = byFieldId.get(f.getId());
            Object out = decodeValue(f, v, storageTenantId, recordId, piiReadPermission);
            outValues.put(f.getSlug(), out);
        }

        // Links
        List<RecordLink> links = linkRepository.findByTenantIdAndFromRecordId(storageTenantId, recordId);
        Map<UUID, EntityRelationship> relationshipsById = new HashMap<>();
        for (EntityRelationship r : relationshipRepository.findByTenantId(storageTenantId)) relationshipsById.put(r.getId(), r);

        List<com.erp.entitybuilder.web.v1.dto.RecordDtos.LinkDto> outLinks = new ArrayList<>();
        for (RecordLink l : links) {
            EntityRelationship rel = relationshipsById.get(l.getRelationshipId());
            String relationshipSlug = rel != null ? rel.getSlug() : null;
            outLinks.add(new com.erp.entitybuilder.web.v1.dto.RecordDtos.LinkDto(relationshipSlug, l.getToRecordId()));
        }

        Set<UUID> labelIds = new HashSet<>();
        if (record.getCreatedBy() != null) {
            labelIds.add(record.getCreatedBy());
        }
        if (record.getUpdatedBy() != null) {
            labelIds.add(record.getUpdatedBy());
        }
        Map<UUID, String> userLabels = recordUserLabelLookup.labelsFor(tenantId, labelIds);
        String createdByLabel = record.getCreatedBy() != null ? userLabels.get(record.getCreatedBy()) : null;
        String updatedByLabel = record.getUpdatedBy() != null ? userLabels.get(record.getUpdatedBy()) : null;

        UUID esId = record.getEntityStatusId();
        String esDisplay = esId != null
                ? entityStatusLabelService.resolveDisplayLabel(esId, currentRequestLanguage())
                : null;

        return new RecordResponse(new com.erp.entitybuilder.web.v1.dto.RecordDtos.RecordDto(
                record.getId(),
                record.getTenantId(),
                record.getEntityId(),
                record.getExternalId(),
                record.getBusinessDocumentNumber(),
                record.getCreatedBy(),
                record.getUpdatedBy(),
                record.getStatus(),
                outValues,
                outLinks,
                record.getCreatedAt(),
                record.getUpdatedAt(),
                createdByLabel,
                updatedByLabel,
                esId,
                esDisplay
        ), outValues);
    }

    private static String currentRequestLanguage() {
        try {
            var attrs = RequestContextHolder.getRequestAttributes();
            if (attrs instanceof ServletRequestAttributes servletRequestAttributes) {
                return RequestLocaleResolver.resolveLanguage(servletRequestAttributes.getRequest());
            }
        } catch (Exception ignored) {
            /* non-request thread */
        }
        return "en";
    }

    private Object decodeValue(EntityField f, EntityRecordValue v, UUID tenantId, UUID recordId, boolean piiReadPermission) {
        if (v == null) return null;
        if (f.isPii()) {
            Optional<PiiVaultEntry> entry = piiVaultRepository.findByTenantIdAndRecordIdAndFieldId(tenantId, recordId, f.getId());
            if (entry.isEmpty()) return null;
            String plain = piiCrypto.decrypt(entry.get().getEncryptedValue());
            if (!piiReadPermission) {
                // v1 masking policy: only mask string PII; for other types return null.
                if (!"string".equalsIgnoreCase(f.getFieldType()) && !"text".equalsIgnoreCase(f.getFieldType())) return null;
                return maskLast4(plain);
            }
            return parseToFieldType(f.getFieldType(), plain);
        }

        return parseFromRecordValue(f.getFieldType(), v);
    }

    private Object parseFromRecordValue(String fieldType, EntityRecordValue v) {
        if (FieldTypes.isNumericFieldType(fieldType)) {
            return v.getValueNumber();
        }
        String t = FieldTypes.normalizeSqlFieldType(fieldType);
        return switch (t) {
            case "string", "text" -> v.getValueText();
            case "date", "datetime" -> v.getValueDate() != null ? v.getValueDate().toString() : null;
            case "boolean" -> v.getValueBoolean();
            case "reference" -> v.getValueReference();
            case "document_number" -> v.getValueText();
            default -> v.getValueText();
        };
    }

    private Object parseToFieldType(String fieldType, String plain) {
        if (plain == null) return null;
        if (FieldTypes.isNumericFieldType(fieldType)) {
            return new BigDecimal(plain);
        }
        return switch (FieldTypes.normalizeSqlFieldType(fieldType)) {
            case "string", "text" -> plain;
            case "date", "datetime" -> Instant.parse(plain);
            case "boolean" -> Boolean.parseBoolean(plain);
            case "reference" -> UUID.fromString(plain);
            case "document_number" -> plain;
            default -> plain;
        };
    }

    private void applyTypedValue(EntityRecordValue rv, String fieldType, Object rawValue) {
        if (FieldTypes.isNumericFieldType(fieldType)) {
            BigDecimal bd = rawValue instanceof Number ? new BigDecimal(rawValue.toString()) : new BigDecimal(String.valueOf(rawValue));
            rv.setValueNumber(bd);
            return;
        }
        switch (FieldTypes.normalizeSqlFieldType(fieldType)) {
            case "string", "text" -> rv.setValueText(String.valueOf(rawValue));
            case "date", "datetime" -> {
                String s = String.valueOf(rawValue);
                Instant inst = Instant.parse(s);
                rv.setValueDate(inst);
            }
            case "boolean" -> {
                if (rawValue instanceof Boolean b) rv.setValueBoolean(b);
                else rv.setValueBoolean(Boolean.parseBoolean(String.valueOf(rawValue)));
            }
            case "reference" -> {
                UUID id = rawValue instanceof UUID u ? u : UUID.fromString(String.valueOf(rawValue));
                rv.setValueReference(id);
            }
            case "document_number" -> rv.setValueText(String.valueOf(rawValue));
            default -> rv.setValueText(String.valueOf(rawValue));
        }
    }

    private String computeRequestHash(Map<String, Object> values, List<LinkInput> links, String externalId, String businessDocumentNumber) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("values", values);
        if (externalId != null) body.put("externalId", externalId);
        if (businessDocumentNumber != null) body.put("businessDocumentNumber", businessDocumentNumber);
        if (links != null) body.put("links", links);

        try {
            String json = canonicalMapper.writeValueAsString(body);
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return toHex(digest);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to canonicalize request body for idempotency hashing", e);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to compute request hash", e);
        }
    }

    private String safeSerialize(Object obj) {
        try {
            return responseMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize response for idempotency", e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static String maskLast4(String source) {
        if (source == null) return null;
        int n = source.length();
        if (n <= 4) return "****";
        String last4 = source.substring(n - 4);
        return "****" + last4;
    }

    private void recomputeSearchVector(UUID recordId, UUID entityId) {
        List<EntityField> fields = fieldRepository.findByEntityId(entityId);
        List<EntityRecordValue> valueRows = valueRepository.findByRecordId(recordId);
        Map<UUID, EntityRecordValue> byFieldId = new HashMap<>();
        for (EntityRecordValue v : valueRows) {
            byFieldId.put(v.getFieldId(), v);
        }
        EntityRecord recordRow = recordRepository.findById(recordId).orElse(null);
        List<String> parts = new ArrayList<>();
        for (EntityField f : fields) {
            if (FieldStorage.isCoreDomain(f) || f.isPii() || !FieldSearchability.isSearchable(f)) {
                continue;
            }
            if (FieldTypes.isDocumentNumber(f)) {
                if (recordRow != null && recordRow.getBusinessDocumentNumber() != null
                        && !recordRow.getBusinessDocumentNumber().isBlank()) {
                    parts.add(recordRow.getBusinessDocumentNumber().trim());
                }
                continue;
            }
            EntityRecordValue v = byFieldId.get(f.getId());
            if (v == null) {
                continue;
            }
            String frag = valueToSearchFragment(f.getFieldType(), v);
            if (frag != null && !frag.isBlank()) {
                parts.add(frag.trim());
            }
        }
        String joined = String.join(" ", parts);
        String normalized = joined.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
        if (normalized.length() > SEARCH_VECTOR_MAX_LEN) {
            normalized = normalized.substring(0, SEARCH_VECTOR_MAX_LEN);
        }
        EntityRecord r = recordRow != null ? recordRow : recordRepository.findById(recordId).orElse(null);
        if (r != null) {
            r.setSearchVector(normalized);
            recordRepository.save(r);
            EntityDefinition entityDef = entityRepository.findById(entityId).orElse(null);
            if (entityDef != null) {
                String title = resolveDisplayLabel(entityDef, fields, byFieldId, r.getTenantId(), recordId, false);
                globalSearchIndexService.upsertEntityRecord(
                        r.getTenantId(),
                        recordId,
                        entityId,
                        normalized,
                        title,
                        entityDef.getName(),
                        entityDef.getSlug()
                );
            }
        }
    }

    private String resolveDisplayLabel(
            EntityDefinition entityDef,
            List<EntityField> fields,
            Map<UUID, EntityRecordValue> byFieldId,
            UUID tenantId,
            UUID recordId,
            boolean piiReadPermission
    ) {
        String ds = entityDef.getDefaultDisplayFieldSlug();
        if (ds != null && !ds.isBlank()) {
            for (EntityField f : fields) {
                if (!f.getSlug().equals(ds)) {
                    continue;
                }
                if (FieldStorage.isCoreDomain(f)) {
                    break;
                }
                Object v = decodeValue(f, byFieldId.get(f.getId()), tenantId, recordId, piiReadPermission);
                if (v != null) {
                    String s = String.valueOf(v);
                    if (!s.isBlank()) {
                        return s;
                    }
                }
                break;
            }
        }
        for (EntityField f : fields) {
            if (FieldStorage.isCoreDomain(f) || f.isPii() || !FieldSearchability.isSearchable(f)) {
                continue;
            }
            EntityRecordValue v = byFieldId.get(f.getId());
            if (v == null) {
                continue;
            }
            String frag = valueToSearchFragment(f.getFieldType(), v);
            if (frag != null && !frag.isBlank()) {
                return frag;
            }
        }
        EntityRecord rec = recordRepository.findById(recordId).orElse(null);
        if (rec != null && rec.getExternalId() != null && !rec.getExternalId().isBlank()) {
            return rec.getExternalId();
        }
        return recordId.toString();
    }

    private static String valueToSearchFragment(String fieldType, EntityRecordValue v) {
        if (v == null) {
            return null;
        }
        if (FieldTypes.isNumericFieldType(fieldType)) {
            return v.getValueNumber() != null ? v.getValueNumber().toPlainString() : null;
        }
        String ft = FieldTypes.normalizeSqlFieldType(fieldType);
        return switch (ft) {
            case "string", "text" -> v.getValueText();
            case "date", "datetime" -> v.getValueDate() != null ? v.getValueDate().toString() : null;
            case "boolean" -> v.getValueBoolean() != null ? v.getValueBoolean().toString() : null;
            case "reference" -> v.getValueReference() != null ? v.getValueReference().toString() : null;
            default -> v.getValueText();
        };
    }

    private void writeEntityRecordCreateAudit(
            UUID tenantId,
            UUID userId,
            UUID correlationId,
            UUID recordId,
            EntityDefinition entity,
            Map<String, Object> requestValues,
            List<LinkInput> links,
            Map<String, EntityField> fieldBySlug
    ) {
        if (auditLogWriter == null) {
            return;
        }
        EntityRecord rec = recordRepository.findById(recordId).orElse(null);
        if (rec == null) {
            return;
        }
        Map<String, Object> afterValues = collectAuditValuesForSlugs(recordId, tenantId, requestValues.keySet(), fieldBySlug);
        List<Map<String, Object>> changes = new ArrayList<>();
        for (String slug : new TreeSet<>(requestValues.keySet())) {
            EntityField f = fieldBySlug.get(slug);
            if (f == null || FieldStorage.isCoreDomain(f)) {
                continue;
            }
            Object newSide = auditNewSideForCreate(f, requestValues.get(slug), afterValues.get(slug));
            Map<String, Object> ch = new LinkedHashMap<>();
            ch.put("path", "values." + slug);
            ch.put("old", null);
            ch.put("new", newSide);
            ch.put("fieldType", f.getFieldType());
            changes.add(ch);
        }
        AuditEvent.Builder b = AuditEvent.builder()
                .tenantId(tenantId)
                .actorId(userId)
                .sourceService(AUDIT_SOURCE_SERVICE)
                .operation(AuditOperations.CREATE)
                .action(AuditActions.ENTITY_RECORD_CREATE)
                .resourceType(AuditResourceTypes.ENTITY_RECORD)
                .resourceId(recordId)
                .correlationId(correlationId)
                .putPayload("changes", changes)
                .putPayload("context", auditContextMap(entity, rec));
        if (links != null) {
            b.putPayload("links", Map.of("after", auditLinkSnapshot(tenantId, recordId)));
        }
        attachActorSnapshot(b);
        auditLogWriter.append(b.build());
    }

    private void writeEntityRecordUpdateAudit(
            UUID tenantId,
            UUID userId,
            UUID correlationId,
            UUID recordId,
            EntityDefinition entityDef,
            EntityRecord record,
            Map<String, Object> requestValues,
            List<LinkInput> links,
            Map<String, EntityField> fieldBySlug,
            Map<String, Object> auditBeforeValues,
            List<Map<String, Object>> auditBeforeLinks
    ) {
        if (auditLogWriter == null) {
            return;
        }
        List<Map<String, Object>> changes = computeUpdateValueChanges(
                recordId, tenantId, requestValues, fieldBySlug, auditBeforeValues
        );
        Map<String, Object> linksBlock = null;
        if (links != null) {
            List<Map<String, Object>> afterLinks = auditLinkSnapshot(tenantId, recordId);
            if (!afterLinks.equals(auditBeforeLinks)) {
                linksBlock = Map.of("before", auditBeforeLinks, "after", afterLinks);
            }
        }
        if (changes.isEmpty() && linksBlock == null) {
            return;
        }
        AuditEvent.Builder b = AuditEvent.builder()
                .tenantId(tenantId)
                .actorId(userId)
                .sourceService(AUDIT_SOURCE_SERVICE)
                .operation(AuditOperations.UPDATE)
                .action(AuditActions.ENTITY_RECORD_UPDATE)
                .resourceType(AuditResourceTypes.ENTITY_RECORD)
                .resourceId(recordId)
                .correlationId(correlationId)
                .putPayload("context", auditContextMap(entityDef, record));
        if (!changes.isEmpty()) {
            b.putPayload("changes", changes);
        }
        if (linksBlock != null) {
            b.putPayload("links", linksBlock);
        }
        attachActorSnapshot(b);
        auditLogWriter.append(b.build());
    }

    private void writeEntityRecordDeleteAudit(
            UUID tenantId,
            UUID userId,
            UUID correlationId,
            UUID recordId,
            EntityDefinition entityDef,
            EntityRecord record,
            Map<String, Object> snapshotValues,
            List<Map<String, Object>> snapshotLinks
    ) {
        if (auditLogWriter == null) {
            return;
        }
        AuditEvent.Builder b = AuditEvent.builder()
                .tenantId(tenantId)
                .actorId(userId)
                .sourceService(AUDIT_SOURCE_SERVICE)
                .operation(AuditOperations.DELETE)
                .action(AuditActions.ENTITY_RECORD_DELETE)
                .resourceType(AuditResourceTypes.ENTITY_RECORD)
                .resourceId(recordId)
                .correlationId(correlationId)
                .putPayload("context", auditContextMap(entityDef, record))
                .putPayload("before", Map.of("values", snapshotValues, "links", snapshotLinks));
        attachActorSnapshot(b);
        auditLogWriter.append(b.build());
    }

    /**
     * Snapshot of the caller for audit UIs when IAM {@code users} is not co-located with {@code audit_log}.
     */
    private static void attachActorSnapshot(AuditEvent.Builder b) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof TenantPrincipal p)) {
            return;
        }
        String email = p.getEmail();
        if (email == null || email.isBlank()) {
            return;
        }
        Map<String, Object> actor = new LinkedHashMap<>();
        actor.put("email", email.trim());
        b.putPayload("actor", actor);
    }

    private List<Map<String, Object>> computeUpdateValueChanges(
            UUID recordId,
            UUID tenantId,
            Map<String, Object> requestValues,
            Map<String, EntityField> fieldBySlug,
            Map<String, Object> auditBeforeValues
    ) {
        Map<String, Object> afterValues = collectAuditValuesForSlugs(recordId, tenantId, requestValues.keySet(), fieldBySlug);
        List<Map<String, Object>> changes = new ArrayList<>();
        for (String slug : new TreeSet<>(requestValues.keySet())) {
            EntityField f = fieldBySlug.get(slug);
            if (f == null || FieldStorage.isCoreDomain(f)) {
                continue;
            }
            Object oldV = auditBeforeValues.get(slug);
            Object newV = afterValues.get(slug);
            if (f.isPii()) {
                if (requestValues.get(slug) != null) {
                    Map<String, Object> ch = new LinkedHashMap<>();
                    ch.put("path", "values." + slug);
                    ch.put("old", oldV);
                    ch.put("new", Map.of("redacted", true, "changed", true));
                    ch.put("fieldType", f.getFieldType());
                    changes.add(ch);
                } else if (!auditValueEquals(oldV, newV)) {
                    Map<String, Object> ch = new LinkedHashMap<>();
                    ch.put("path", "values." + slug);
                    ch.put("old", oldV);
                    ch.put("new", newV);
                    ch.put("fieldType", f.getFieldType());
                    changes.add(ch);
                }
            } else if (!auditValueEquals(oldV, newV)) {
                Map<String, Object> ch = new LinkedHashMap<>();
                ch.put("path", "values." + slug);
                ch.put("old", oldV);
                ch.put("new", newV);
                ch.put("fieldType", f.getFieldType());
                changes.add(ch);
            }
        }
        return changes;
    }

    private static Object auditNewSideForCreate(EntityField f, Object requestRaw, Object afterSnapshot) {
        if (f.isPii() && requestRaw != null) {
            return Map.of("redacted", true, "changed", true);
        }
        return afterSnapshot;
    }

    private Map<String, Object> collectAuditValuesForSlugs(
            UUID recordId,
            UUID tenantId,
            Set<String> slugs,
            Map<String, EntityField> fieldBySlug
    ) {
        List<EntityRecordValue> rows = valueRepository.findByRecordId(recordId);
        Map<UUID, EntityRecordValue> byFieldId = new HashMap<>();
        for (EntityRecordValue v : rows) {
            byFieldId.put(v.getFieldId(), v);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (String slug : new TreeSet<>(slugs)) {
            EntityField f = fieldBySlug.get(slug);
            if (f == null || FieldStorage.isCoreDomain(f)) {
                continue;
            }
            EntityRecordValue v = byFieldId.get(f.getId());
            out.put(slug, auditValueSnapshot(f, v, tenantId, recordId));
        }
        return out;
    }

    private Object auditValueSnapshot(EntityField f, EntityRecordValue v, UUID tenantId, UUID recordId) {
        if (FieldStorage.isCoreDomain(f)) {
            return null;
        }
        if (f.isPii()) {
            return piiVaultRepository.findByTenantIdAndRecordIdAndFieldId(tenantId, recordId, f.getId()).isPresent()
                    ? Map.of("redacted", true)
                    : null;
        }
        if (v == null) {
            return null;
        }
        return parseFromRecordValue(f.getFieldType(), v);
    }

    private List<Map<String, Object>> auditLinkSnapshot(UUID tenantId, UUID recordId) {
        List<RecordLink> links = linkRepository.findByTenantIdAndFromRecordId(tenantId, recordId);
        Map<UUID, EntityRelationship> relById = new HashMap<>();
        for (EntityRelationship r : relationshipRepository.findByTenantId(tenantId)) {
            relById.put(r.getId(), r);
        }
        List<Map<String, Object>> list = new ArrayList<>();
        for (RecordLink l : links) {
            EntityRelationship rel = relById.get(l.getRelationshipId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("relationshipSlug", rel != null ? rel.getSlug() : null);
            m.put("toRecordId", l.getToRecordId());
            list.add(m);
        }
        list.sort(Comparator.comparing(m -> String.valueOf(m.get("relationshipSlug")) + ":" + m.get("toRecordId")));
        return list;
    }

    private static Map<String, Object> auditContextMap(EntityDefinition entity, EntityRecord record) {
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("entityId", entity.getId());
        ctx.put("entitySlug", entity.getSlug());
        if (record.getExternalId() != null && !record.getExternalId().isBlank()) {
            ctx.put("externalId", record.getExternalId());
        }
        if (record.getBusinessDocumentNumber() != null && !record.getBusinessDocumentNumber().isBlank()) {
            ctx.put("businessDocumentNumber", record.getBusinessDocumentNumber());
        }
        return ctx;
    }

    private static boolean auditValueEquals(Object a, Object b) {
        if (a == null && b == null) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        if (a instanceof Map<?, ?> && b instanceof Map<?, ?>) {
            return a.equals(b);
        }
        if (a instanceof BigDecimal ba && b instanceof BigDecimal bb) {
            return ba.compareTo(bb) == 0;
        }
        return Objects.equals(a, b);
    }

    // --- Helper types ---

    public record LinkInput(String relationshipSlug, UUID toRecordId) {}

    public record PageResult(List<RecordDtos.RecordDto> items, int page, int pageSize, long total) {}

    public record LinksResult(List<RecordDtos.LinkDto> links) {}

    public record RecordResponse(com.erp.entitybuilder.web.v1.dto.RecordDtos.RecordDto recordDto, Map<String, Object> values) {
        public com.erp.entitybuilder.web.v1.dto.RecordDtos.RecordDto getRecordDto() {
            return recordDto;
        }

        public static RecordResponse fromJson(String json, boolean piiReadPermission) {
            // In v1, we store response_json already masked appropriately for the user at creation time.
            // We'll deserialize into RecordDto and return.
            try {
                com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
                com.erp.entitybuilder.web.v1.dto.RecordDtos.RecordDto dto = om.readValue(json, com.erp.entitybuilder.web.v1.dto.RecordDtos.RecordDto.class);
                return new RecordResponse(dto, dto.values());
            } catch (Exception e) {
                // If deserialize fails, return an empty but valid response.
                return new RecordResponse(new com.erp.entitybuilder.web.v1.dto.RecordDtos.RecordDto(
                        null, null, null, null, null, null, null, null, Map.of(), List.of(), null, null, null, null,
                        null, null
                ), Map.of());
            }
        }
    }

    private int clampPageSize(int pageSize) {
        if (pageSize <= 0) return 50;
        return Math.min(pageSize, 200);
    }

    private boolean isValueNonNull(String fieldType, EntityRecordValue rv) {
        if (FieldTypes.isNumericFieldType(fieldType)) {
            return rv.getValueNumber() != null;
        }
        return switch (FieldTypes.normalizeSqlFieldType(fieldType)) {
            case "string", "text" -> rv.getValueText() != null;
            case "date", "datetime" -> rv.getValueDate() != null;
            case "boolean" -> rv.getValueBoolean() != null;
            case "reference" -> rv.getValueReference() != null;
            case "document_number" -> rv.getValueText() != null;
            default -> rv.getValueText() != null;
        };
    }

    /**
     * Resolves {@link EntityRecord#getBusinessDocumentNumber()} from the request property and/or
     * {@code values} entries for fields with type {@link FieldTypes#DOCUMENT_NUMBER}.
     */
    private static String resolveBusinessDocumentNumberForCreate(
            String requestBusinessDocumentNumber,
            Map<String, Object> values,
            Map<String, EntityField> fieldBySlug
    ) {
        String fromRequest = requestBusinessDocumentNumber != null && !requestBusinessDocumentNumber.isBlank()
                ? requestBusinessDocumentNumber.trim()
                : null;
        String fromValues = null;
        for (EntityField f : fieldBySlug.values()) {
            if (!FieldTypes.isDocumentNumber(f)) {
                continue;
            }
            Object v = values.get(f.getSlug());
            if (v == null) {
                continue;
            }
            String s = String.valueOf(v).trim();
            if (s.isEmpty()) {
                continue;
            }
            if (fromValues != null && !fromValues.equals(s)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request",
                        "Conflicting values for document_number fields", Map.of());
            }
            fromValues = s;
        }
        if (fromRequest != null && fromValues != null && !fromRequest.equals(fromValues)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request",
                    "businessDocumentNumber must match document_number field in values", Map.of());
        }
        return fromRequest != null ? fromRequest : fromValues;
    }

    private static ApiException missingRequiredFieldOnCreate(EntityField f) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("field", f.getSlug());
        d.put("fieldName", f.getName());
        d.put("fieldType", f.getFieldType());
        String msg = "Missing required field \"" + f.getName() + "\" (slug: " + f.getSlug() + ", type: " + f.getFieldType() + ")";
        return new ApiException(HttpStatus.BAD_REQUEST, "bad_request", msg, d);
    }

    private static ApiException missingRequiredFieldAfterUpdate(EntityField f, String reasonCode, String reasonDetail) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("field", f.getSlug());
        d.put("fieldName", f.getName());
        d.put("fieldType", f.getFieldType());
        d.put("reason", reasonCode);
        String msg = "Missing required value after update for \"" + f.getName() + "\" (slug: " + f.getSlug() + ", type: "
                + f.getFieldType() + "): " + reasonDetail;
        return new ApiException(HttpStatus.BAD_REQUEST, "bad_request", msg, d);
    }

    private static Map<String, Object> eavValuesOnly(Map<String, Object> values, Map<String, EntityField> fieldBySlug) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : values.entrySet()) {
            EntityField f = fieldBySlug.get(e.getKey());
            if (f != null && FieldStorage.isEavExtension(f) && !FieldTypes.isDocumentNumber(f)) {
                out.put(e.getKey(), e.getValue());
            }
        }
        return out;
    }

    private static void applyOptimisticVersionDefaultOnCreate(Map<String, Object> values, List<EntityField> fields) {
        for (EntityField f : fields) {
            if (!FieldTypes.isOptimisticVersionField(f)) {
                continue;
            }
            if (!values.containsKey(f.getSlug()) || values.get(f.getSlug()) == null) {
                values.put(f.getSlug(), BigDecimal.ZERO);
            }
        }
    }

    private static BigDecimal readOptimisticVersion(EntityRecordValueRepository valueRepository, UUID recordId, UUID fieldId) {
        return valueRepository.findByRecordIdAndFieldId(recordId, fieldId)
                .map(EntityRecordValue::getValueNumber)
                .filter(Objects::nonNull)
                .orElse(BigDecimal.ZERO);
    }

    private void writeOptimisticVersionValue(UUID recordId, UUID fieldId, BigDecimal version) {
        EntityRecordValue rv = valueRepository.findByRecordIdAndFieldId(recordId, fieldId)
                .orElseGet(() -> {
                    EntityRecordValue x = new EntityRecordValue();
                    x.setRecordId(recordId);
                    x.setFieldId(fieldId);
                    return x;
                });
        rv.setValueText(null);
        rv.setValueNumber(version);
        rv.setValueDate(null);
        rv.setValueBoolean(null);
        rv.setValueReference(null);
        valueRepository.save(rv);
    }

    private static BigDecimal coerceToBigDecimal(Object raw) {
        if (raw instanceof BigDecimal bd) {
            return bd;
        }
        if (raw instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        return new BigDecimal(String.valueOf(raw).trim());
    }

    private void upsertLinkStrict(UUID requestingTenantId, UUID fromRecordId, UUID fromEntityId, LinkInput link, boolean allowIdempotent) {
        EntityRecord fromRec = recordRepository.findById(fromRecordId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "From record not found"));
        if (!recordVisibleToTenant(fromRec, requestingTenantId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "not_found", "From record not found");
        }
        if (!fromEntityId.equals(fromRec.getEntityId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "fromEntityId mismatch");
        }
        UUID linkTenant = fromRec.getTenantId();
        EntityRelationship rel = resolveRelationshipForLink(linkTenant, link.relationshipSlug());
        if (!rel.getFromEntityId().equals(fromEntityId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Relationship is not valid for this entity", Map.of("relationshipSlug", link.relationshipSlug()));
        }
        EntityRecord toRecord = recordRepository.findById(link.toRecordId())
                .filter(r -> recordVisibleToTenant(r, requestingTenantId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Target record not found", Map.of("toRecordId", link.toRecordId())));
        if (!toRecord.getEntityId().equals(rel.getToEntityId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Relationship target entity mismatch");
        }

        if ("one-to-one".equalsIgnoreCase(rel.getCardinality())) {
            Optional<RecordLink> existingLink = linkRepository.findFirstByTenantIdAndFromRecordIdAndRelationshipId(linkTenant, fromRecordId, rel.getId());
            if (existingLink.isPresent()) {
                RecordLink l = existingLink.get();
                if (!l.getToRecordId().equals(link.toRecordId())) {
                    throw new ApiException(HttpStatus.CONFLICT, "conflict", "one-to-one relationship already has a linked record");
                }
                if (!allowIdempotent) return;
                return;
            }
        }

        if (!linkRepository.existsByTenantIdAndFromRecordIdAndRelationshipIdAndToRecordId(linkTenant, fromRecordId, rel.getId(), link.toRecordId())) {
            RecordLink rl = new RecordLink();
            rl.setTenantId(linkTenant);
            rl.setFromRecordId(fromRecordId);
            rl.setToRecordId(link.toRecordId());
            rl.setRelationshipId(rel.getId());
            linkRepository.save(rl);
        }
    }

    private EntityRelationship resolveRelationshipForLink(UUID primaryTenantId, String relationshipSlug) {
        Optional<EntityRelationship> rel = relationshipRepository.findByTenantIdAndSlug(primaryTenantId, relationshipSlug);
        if (rel.isPresent()) {
            return rel.get();
        }
        if (platformTenantProperties.isConfigured()) {
            return relationshipRepository.findByTenantIdAndSlug(platformTenantProperties.getTenantId(), relationshipSlug)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Relationship not found", Map.of("relationshipSlug", relationshipSlug)));
        }
        throw new ApiException(HttpStatus.NOT_FOUND, "not_found", "Relationship not found", Map.of("relationshipSlug", relationshipSlug));
    }
}

