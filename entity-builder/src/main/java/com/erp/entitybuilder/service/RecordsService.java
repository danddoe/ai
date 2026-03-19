package com.erp.entitybuilder.service;

import com.erp.entitybuilder.domain.*;
import com.erp.entitybuilder.repository.*;
import com.erp.entitybuilder.web.ApiException;
import com.erp.entitybuilder.web.v1.dto.RecordDtos;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

@Service
public class RecordsService {

    private static final String CREATE_RECORD_ROUTE_TEMPLATE = "/v1/tenants/{tenantId}/entities/{entityId}/records";
    private static final String POST_METHOD = "POST";

    private final EntityDefinitionRepository entityRepository;
    private final EntityFieldRepository fieldRepository;
    private final EntityRecordRepository recordRepository;
    private final EntityRecordValueRepository valueRepository;
    private final PiiVaultRepository piiVaultRepository;
    private final RecordLinkRepository linkRepository;
    private final EntityRelationshipRepository relationshipRepository;
    private final IdempotencyRequestRepository idempotencyRepository;
    private final PiiCrypto piiCrypto;

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
            PiiCrypto piiCrypto
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
            Map<String, Object> values,
            List<LinkInput> links,
            String idempotencyKey,
            boolean piiReadPermission
    ) {
        Objects.requireNonNull(values, "values required");

        // Idempotency: try lookup first
        String requestHash = null;
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            requestHash = computeRequestHash(values, links, externalId);
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
                    // Return stored response without recomputing (keeps PII masking stable for the same caller/user).
                    return RecordResponse.fromJson(ir.getResponseJson(), piiReadPermission);
                }
            }
        }

        // externalId dedupe
        if (externalId != null && !externalId.isBlank()) {
            Optional<EntityRecord> existingRecord = recordRepository.findByTenantIdAndEntityIdAndExternalId(tenantId, entityId, externalId);
            if (existingRecord.isPresent()) {
                return toResponse(existingRecord.get().getId(), tenantId, piiReadPermission);
            }
        }

        // Load schema (entity + fields)
        EntityDefinition entity = entityRepository.findById(entityId)
                .filter(e -> tenantId.equals(e.getTenantId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Entity not found"));

        List<EntityField> fields = fieldRepository.findByEntityId(entityId);
        Map<String, EntityField> fieldBySlug = new HashMap<>();
        for (EntityField f : fields) fieldBySlug.put(f.getSlug(), f);

        // Validate required
        for (EntityField f : fields) {
            if (f.isRequired()) {
                if (!values.containsKey(f.getSlug()) || values.get(f.getSlug()) == null) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Missing required field", Map.of("field", f.getSlug()));
                }
            }
        }

        // Validate unknown fields
        for (String providedSlug : values.keySet()) {
            if (!fieldBySlug.containsKey(providedSlug)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Unknown field slug", Map.of("field", providedSlug));
            }
        }

        // Create record row
        EntityRecord record = new EntityRecord();
        record.setTenantId(tenantId);
        record.setEntityId(entity.getId());
        record.setExternalId(externalId != null && !externalId.isBlank() ? externalId : null);
        record.setCreatedBy(userId);
        record.setStatus("ACTIVE");
        record = recordRepository.save(record);
        UUID recordId = record.getId();

        // Save field values
        for (EntityField f : fields) {
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
                EntityRelationship rel = relationshipRepository.findByTenantIdAndSlug(tenantId, link.relationshipSlug())
                        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Relationship not found", Map.of("relationshipSlug", link.relationshipSlug())));
                // from side must match entity
                if (!rel.getFromEntityId().equals(entityId)) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Relationship is not valid for this entity", Map.of("relationshipSlug", link.relationshipSlug()));
                }
                UUID toRecordId = link.toRecordId();
                EntityRecord toRecord = recordRepository.findById(toRecordId)
                        .filter(r -> tenantId.equals(r.getTenantId()))
                        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Target record not found", Map.of("toRecordId", toRecordId)));

                if (!toRecord.getEntityId().equals(rel.getToEntityId())) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Relationship target entity mismatch");
                }

                if ("one-to-one".equalsIgnoreCase(rel.getCardinality())) {
                    Optional<RecordLink> existingLink = linkRepository.findFirstByTenantIdAndFromRecordIdAndRelationshipId(tenantId, recordId, rel.getId());
                    if (existingLink.isPresent()) {
                        RecordLink l = existingLink.get();
                        if (!l.getToRecordId().equals(toRecordId)) {
                            throw new ApiException(HttpStatus.CONFLICT, "conflict", "one-to-one relationship already has a linked record");
                        }
                        // else same target is idempotent for this link
                    }
                }

                if (!linkRepository.existsByTenantIdAndFromRecordIdAndRelationshipIdAndToRecordId(tenantId, recordId, rel.getId(), toRecordId)) {
                    RecordLink rl = new RecordLink();
                    rl.setTenantId(tenantId);
                    rl.setFromRecordId(recordId);
                    rl.setToRecordId(toRecordId);
                    rl.setRelationshipId(rel.getId());
                    linkRepository.save(rl);
                }
            }
        }

        // Build response
        RecordResponse response = toResponse(recordId, tenantId, piiReadPermission);

        // Persist idempotency entry after success
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            // if request_hash wasn't computed (shouldn't happen), compute it now.
            if (requestHash == null) requestHash = computeRequestHash(values, links, externalId);
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
                .filter(r -> tenantId.equals(r.getTenantId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Record not found"));

        return toResponse(record.getId(), tenantId, piiReadPermission);
    }

    @Transactional(readOnly = true)
    public PageResult listRecords(UUID tenantId, UUID entityId, int page, int pageSize, boolean piiReadPermission) {
        // page is 1-based in the API layer
        int pageIndex = Math.max(0, page - 1);
        int size = clampPageSize(pageSize);

        var p = recordRepository.findByTenantIdAndEntityId(tenantId, entityId, org.springframework.data.domain.PageRequest.of(pageIndex, size));
        List<RecordDtos.RecordDto> items = p.getContent().stream()
                .map(r -> toResponse(r.getId(), tenantId, piiReadPermission).getRecordDto())
                .toList();
        return new PageResult(items, page, size, p.getTotalElements());
    }

    @Transactional
    public RecordResponse updateRecord(
            UUID tenantId,
            UUID userId,
            UUID entityId,
            UUID recordId,
            Map<String, Object> values,
            List<LinkInput> links,
            boolean piiReadPermission
    ) {
        EntityRecord record = recordRepository.findById(recordId)
                .filter(r -> tenantId.equals(r.getTenantId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Record not found"));
        if (!record.getEntityId().equals(entityId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "not_found", "Record not found");
        }

        if (values == null) values = Map.of();
        List<EntityField> fields = fieldRepository.findByEntityId(entityId);
        Map<String, EntityField> fieldBySlug = new HashMap<>();
        for (EntityField f : fields) fieldBySlug.put(f.getSlug(), f);

        for (String providedSlug : values.keySet()) {
            if (!fieldBySlug.containsKey(providedSlug)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Unknown field slug", Map.of("field", providedSlug));
            }
        }

        // Apply field updates (partial merge)
        for (var e : values.entrySet()) {
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
                    piiVaultRepository.findByTenantIdAndRecordIdAndFieldId(tenantId, recordId, fieldId)
                            .ifPresent(p -> piiVaultRepository.delete(p));
                }
                continue;
            }

            if (f.isPii()) {
                String plain = String.valueOf(rawValue);
                PiiCrypto.EncryptedValue enc = piiCrypto.encrypt(plain);

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
                valueRepository.save(rv);
            } else {
                applyTypedValue(rv, f.getFieldType(), rawValue);
                valueRepository.save(rv);
            }
        }

        // Enforce required non-null after merge
        for (EntityField f : fields) {
            if (!f.isRequired()) continue;
            UUID fieldId = f.getId();
            if (f.isPii()) {
                boolean hasPii = piiVaultRepository.findByTenantIdAndRecordIdAndFieldId(tenantId, recordId, fieldId).isPresent();
                if (!hasPii) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Missing required field after update", Map.of("field", f.getSlug()));
                }
            } else {
                EntityRecordValue rv = valueRepository.findByRecordIdAndFieldId(recordId, fieldId)
                        .orElse(null);
                if (rv == null || !isValueNonNull(f.getFieldType(), rv)) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Missing required field after update", Map.of("field", f.getSlug()));
                }
            }
        }

        // Links replace semantics
        if (links != null) {
            linkRepository.deleteByTenantIdAndFromRecordId(tenantId, recordId);
            for (LinkInput link : links) {
                upsertLinkStrict(tenantId, recordId, entityId, link, true);
            }
        }

        return toResponse(recordId, tenantId, piiReadPermission);
    }

    @Transactional
    public void deleteRecord(UUID tenantId, UUID entityId, UUID recordId) {
        EntityRecord record = recordRepository.findById(recordId)
                .filter(r -> tenantId.equals(r.getTenantId()) && entityId.equals(r.getEntityId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Record not found"));
        recordRepository.delete(record);
    }

    @Transactional(readOnly = true)
    public LinksResult listLinks(UUID tenantId, UUID fromRecordId) {
        List<RecordLink> links = linkRepository.findByTenantIdAndFromRecordId(tenantId, fromRecordId);
        Map<UUID, EntityRelationship> relationshipsById = new HashMap<>();
        for (EntityRelationship r : relationshipRepository.findByTenantId(tenantId)) relationshipsById.put(r.getId(), r);

        List<com.erp.entitybuilder.web.v1.dto.RecordDtos.LinkDto> outLinks = new ArrayList<>();
        for (RecordLink l : links) {
            EntityRelationship rel = relationshipsById.get(l.getRelationshipId());
            outLinks.add(new com.erp.entitybuilder.web.v1.dto.RecordDtos.LinkDto(rel != null ? rel.getSlug() : null, l.getToRecordId()));
        }
        return new LinksResult(outLinks);
    }

    @Transactional
    public void addLink(UUID tenantId, UUID fromRecordId, String relationshipSlug, UUID toRecordId) {
        EntityRecord fromRecord = recordRepository.findById(fromRecordId)
                .filter(r -> tenantId.equals(r.getTenantId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "From record not found"));
        upsertLinkStrict(tenantId, fromRecordId, fromRecord.getEntityId(), new LinkInput(relationshipSlug, toRecordId), false);
    }

    @Transactional
    public void deleteLink(UUID tenantId, UUID fromRecordId, String relationshipSlug, UUID toRecordId) {
        EntityRelationship rel = relationshipRepository.findByTenantIdAndSlug(tenantId, relationshipSlug)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Relationship not found", Map.of("relationshipSlug", relationshipSlug)));
        RecordLink l = linkRepository.findByTenantIdAndFromRecordIdAndRelationshipIdAndToRecordId(tenantId, fromRecordId, rel.getId(), toRecordId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Link not found"));
        linkRepository.delete(l);
    }

    private RecordResponse toResponse(UUID recordId, UUID tenantId, boolean piiReadPermission) {
        EntityRecord record = recordRepository.findById(recordId)
                .filter(r -> tenantId.equals(r.getTenantId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Record not found"));

        List<EntityField> fields = fieldRepository.findByEntityId(record.getEntityId());
        // Load value rows
        List<EntityRecordValue> values = valueRepository.findByRecordId(recordId);
        Map<UUID, EntityRecordValue> byFieldId = new HashMap<>();
        for (EntityRecordValue v : values) byFieldId.put(v.getFieldId(), v);

        Map<String, Object> outValues = new LinkedHashMap<>();
        for (EntityField f : fields) {
            EntityRecordValue v = byFieldId.get(f.getId());
            Object out = decodeValue(f, v, tenantId, recordId, piiReadPermission);
            outValues.put(f.getSlug(), out);
        }

        // Links
        List<RecordLink> links = linkRepository.findByTenantIdAndFromRecordId(tenantId, recordId);
        Map<UUID, EntityRelationship> relationshipsById = new HashMap<>();
        for (EntityRelationship r : relationshipRepository.findByTenantId(tenantId)) relationshipsById.put(r.getId(), r);

        List<com.erp.entitybuilder.web.v1.dto.RecordDtos.LinkDto> outLinks = new ArrayList<>();
        for (RecordLink l : links) {
            EntityRelationship rel = relationshipsById.get(l.getRelationshipId());
            String relationshipSlug = rel != null ? rel.getSlug() : null;
            outLinks.add(new com.erp.entitybuilder.web.v1.dto.RecordDtos.LinkDto(relationshipSlug, l.getToRecordId()));
        }

        return new RecordResponse(new com.erp.entitybuilder.web.v1.dto.RecordDtos.RecordDto(
                record.getId(),
                record.getTenantId(),
                record.getEntityId(),
                record.getExternalId(),
                record.getCreatedBy(),
                record.getStatus(),
                outValues,
                outLinks,
                record.getCreatedAt(),
                record.getUpdatedAt()
        ), outValues);
    }

    private Object decodeValue(EntityField f, EntityRecordValue v, UUID tenantId, UUID recordId, boolean piiReadPermission) {
        if (v == null) return null;
        if (f.isPii()) {
            Optional<PiiVaultEntry> entry = piiVaultRepository.findByTenantIdAndRecordIdAndFieldId(tenantId, recordId, f.getId());
            if (entry.isEmpty()) return null;
            String plain = piiCrypto.decrypt(entry.get().getEncryptedValue());
            if (!piiReadPermission) {
                // v1 masking policy: only mask string PII; for other types return null.
                if (!"string".equalsIgnoreCase(f.getFieldType())) return null;
                return maskLast4(plain);
            }
            return parseToFieldType(f.getFieldType(), plain);
        }

        return parseFromRecordValue(f.getFieldType(), v);
    }

    private Object parseFromRecordValue(String fieldType, EntityRecordValue v) {
        return switch (fieldType.toLowerCase(Locale.ROOT)) {
            case "string" -> v.getValueText();
            case "number" -> v.getValueNumber();
            case "date" -> v.getValueDate() != null ? v.getValueDate().toString() : null;
            case "boolean" -> v.getValueBoolean();
            case "reference" -> v.getValueReference();
            default -> v.getValueText();
        };
    }

    private Object parseToFieldType(String fieldType, String plain) {
        if (plain == null) return null;
        return switch (fieldType.toLowerCase(Locale.ROOT)) {
            case "string" -> plain;
            case "number" -> new BigDecimal(plain);
            case "date" -> Instant.parse(plain);
            case "boolean" -> Boolean.parseBoolean(plain);
            case "reference" -> UUID.fromString(plain);
            default -> plain;
        };
    }

    private void applyTypedValue(EntityRecordValue rv, String fieldType, Object rawValue) {
        switch (fieldType.toLowerCase(Locale.ROOT)) {
            case "string" -> rv.setValueText(String.valueOf(rawValue));
            case "number" -> {
                BigDecimal bd = rawValue instanceof Number ? new BigDecimal(rawValue.toString()) : new BigDecimal(String.valueOf(rawValue));
                rv.setValueNumber(bd);
            }
            case "date" -> {
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
            default -> rv.setValueText(String.valueOf(rawValue));
        }
    }

    private String computeRequestHash(Map<String, Object> values, List<LinkInput> links, String externalId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("values", values);
        if (externalId != null) body.put("externalId", externalId);
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
                        null, null, null, null, null, null, Map.of(), List.of(), null, null
                ), Map.of());
            }
        }
    }

    private int clampPageSize(int pageSize) {
        if (pageSize <= 0) return 50;
        return Math.min(pageSize, 200);
    }

    private boolean isValueNonNull(String fieldType, EntityRecordValue rv) {
        return switch (fieldType.toLowerCase(Locale.ROOT)) {
            case "string" -> rv.getValueText() != null;
            case "number" -> rv.getValueNumber() != null;
            case "date" -> rv.getValueDate() != null;
            case "boolean" -> rv.getValueBoolean() != null;
            case "reference" -> rv.getValueReference() != null;
            default -> rv.getValueText() != null;
        };
    }

    private void upsertLinkStrict(UUID tenantId, UUID fromRecordId, UUID fromEntityId, LinkInput link, boolean allowIdempotent) {
        EntityRelationship rel = relationshipRepository.findByTenantIdAndSlug(tenantId, link.relationshipSlug())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Relationship not found", Map.of("relationshipSlug", link.relationshipSlug())));
        if (!rel.getFromEntityId().equals(fromEntityId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Relationship is not valid for this entity", Map.of("relationshipSlug", link.relationshipSlug()));
        }
        EntityRecord toRecord = recordRepository.findById(link.toRecordId())
                .filter(r -> tenantId.equals(r.getTenantId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Target record not found", Map.of("toRecordId", link.toRecordId())));
        if (!toRecord.getEntityId().equals(rel.getToEntityId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Relationship target entity mismatch");
        }

        if ("one-to-one".equalsIgnoreCase(rel.getCardinality())) {
            Optional<RecordLink> existingLink = linkRepository.findFirstByTenantIdAndFromRecordIdAndRelationshipId(tenantId, fromRecordId, rel.getId());
            if (existingLink.isPresent()) {
                RecordLink l = existingLink.get();
                if (!l.getToRecordId().equals(link.toRecordId())) {
                    throw new ApiException(HttpStatus.CONFLICT, "conflict", "one-to-one relationship already has a linked record");
                }
                if (!allowIdempotent) return;
                return;
            }
        }

        if (!linkRepository.existsByTenantIdAndFromRecordIdAndRelationshipIdAndToRecordId(tenantId, fromRecordId, rel.getId(), link.toRecordId())) {
            RecordLink rl = new RecordLink();
            rl.setTenantId(tenantId);
            rl.setFromRecordId(fromRecordId);
            rl.setToRecordId(link.toRecordId());
            rl.setRelationshipId(rel.getId());
            linkRepository.save(rl);
        }
    }
}

