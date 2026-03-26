package com.erp.entitybuilder.service.ddl;

import com.erp.entitybuilder.domain.EntityCategoryKeys;
import com.erp.entitybuilder.domain.EntityDefinition;
import com.erp.entitybuilder.domain.EntityField;
import com.erp.entitybuilder.domain.EntityRelationship;
import com.erp.entitybuilder.repository.EntityDefinitionRepository;
import com.erp.entitybuilder.service.EntitySchemaService;
import com.erp.entitybuilder.service.RelationshipSchemaService;
import com.erp.entitybuilder.service.storage.FieldStorage;
import com.erp.entitybuilder.web.ApiException;
import com.erp.entitybuilder.web.v1.dto.DdlImportDtos;
import net.sf.jsqlparser.JSQLParserException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class DdlEntityImportService {

    private static final Set<String> AUDIT_SLUGS = Set.of("created_at", "updated_at", "deleted_at");

    private final EntityDefinitionRepository entityRepository;
    private final EntitySchemaService schemaService;
    private final RelationshipSchemaService relationshipSchemaService;

    public DdlEntityImportService(
            EntityDefinitionRepository entityRepository,
            EntitySchemaService schemaService,
            RelationshipSchemaService relationshipSchemaService
    ) {
        this.entityRepository = entityRepository;
        this.schemaService = schemaService;
        this.relationshipSchemaService = relationshipSchemaService;
    }

    @Transactional(readOnly = true)
    public DdlImportDtos.DdlImportPreviewResponse preview(UUID tenantId, DdlImportDtos.DdlImportPreviewRequest req) {
        List<ParsedDdlTable> parsed = parseDdl(req.getDdl());
        StorageMode mode = parseStorageMode(req.getStorageMode());
        validateCoreService(mode, req.getCoreBindingService());

        boolean excludeAudit = !Boolean.FALSE.equals(req.getExcludeAuditColumns());
        Set<String> skipSlugs = skipSlugSet(req);

        Set<String> batchSlugs = new HashSet<>();
        for (ParsedDdlTable t : parsed) {
            batchSlugs.add(t.entitySlug());
        }

        List<String> globalWarnings = new ArrayList<>();
        List<DdlImportDtos.TablePreviewDto> tablePreviews = new ArrayList<>();

        for (ParsedDdlTable table : parsed) {
            List<DdlImportDtos.FieldPreviewDto> fieldRows = new ArrayList<>();
            String defaultDisplay = null;

            for (ParsedDdlColumn col : table.columns()) {
                if (skipSlugs.contains(col.columnSlug().toLowerCase(Locale.ROOT))) {
                    continue;
                }
                if (excludeAudit && AUDIT_SLUGS.contains(col.columnSlug().toLowerCase(Locale.ROOT))) {
                    globalWarnings.add("Excluded audit column \"" + col.rawColumnName() + "\" on table " + table.rawTableName());
                    continue;
                }

                ParsedDdlForeignKey fk = col.foreignKey();
                String targetSlug = fk != null ? fkTargetEntitySlug(fk) : null;
                boolean targetResolvable = false;
                if (targetSlug != null) {
                    targetResolvable = batchSlugs.contains(targetSlug)
                            || entityRepository.existsByTenantIdAndSlug(tenantId, targetSlug);
                }

                String fieldType = (fk != null && targetResolvable)
                        ? "reference"
                        : mapSqlTypeToFieldType(col.sqlDataType());

                boolean required = col.notNull() && !col.hasDefault();
                List<String> fw = new ArrayList<>();
                if (fk != null && !targetResolvable) {
                    fw.add("target_entity_not_found");
                }

                fieldRows.add(new DdlImportDtos.FieldPreviewDto(
                        col.rawColumnName(),
                        col.columnSlug(),
                        col.sqlDataType(),
                        fieldType,
                        required,
                        col.primaryKeyColumn(),
                        fk != null ? fk.referencedTableRaw() : null,
                        targetSlug,
                        fk != null ? targetResolvable : null,
                        fw
                ));

                if (defaultDisplay == null && !col.primaryKeyColumn()
                        && !"id".equalsIgnoreCase(col.columnSlug())
                        && ("string".equals(fieldType) || "text".equals(fieldType))) {
                    defaultDisplay = col.columnSlug();
                }
            }

            if (defaultDisplay == null && !fieldRows.isEmpty()) {
                defaultDisplay = fieldRows.stream()
                        .filter(f -> !f.primaryKey() && !"id".equalsIgnoreCase(f.fieldSlug()))
                        .map(DdlImportDtos.FieldPreviewDto::fieldSlug)
                        .findFirst()
                        .orElse(fieldRows.get(0).fieldSlug());
            }

            boolean relFlag = Boolean.TRUE.equals(req.getCreateRelationshipsFromForeignKeys());
            List<DdlImportDtos.RelationshipPreviewDto> relPreviews = new ArrayList<>();
            if (relFlag) {
                for (ParsedDdlColumn col : table.columns()) {
                    ParsedDdlForeignKey fk = col.foreignKey();
                    if (fk == null) {
                        continue;
                    }
                    String parentSlug = fkTargetEntitySlug(fk);
                    String childSlug = table.entitySlug();
                    boolean parentOk = batchSlugs.contains(parentSlug)
                            || entityRepository.existsByTenantIdAndSlug(tenantId, parentSlug);
                    boolean childOk = batchSlugs.contains(childSlug)
                            || entityRepository.existsByTenantIdAndSlug(tenantId, childSlug);
                    boolean creatable = parentOk && childOk;
                    String skipReason = null;
                    if (!parentOk) {
                        skipReason = "referenced_entity_missing";
                    } else if (!childOk) {
                        skipReason = "child_entity_missing";
                    }
                    String relSlug = relationshipSlug(childSlug, col.columnSlug(), parentSlug);
                    String relName = DdlSlugUtil.humanizeSlug(parentSlug) + " → " + DdlSlugUtil.humanizeSlug(childSlug);
                    relPreviews.add(new DdlImportDtos.RelationshipPreviewDto(
                            relSlug,
                            relName,
                            "one-to-many",
                            parentSlug,
                            childSlug,
                            DdlSlugUtil.toSlug(fk.referencedColumnRaw(), 100),
                            col.columnSlug(),
                            creatable,
                            creatable ? null : skipReason
                    ));
                }
            }

            String entityName = DdlSlugUtil.humanizeSlug(table.entitySlug());
            tablePreviews.add(new DdlImportDtos.TablePreviewDto(
                    table.rawTableName(),
                    table.entitySlug(),
                    entityName,
                    defaultDisplay,
                    fieldRows,
                    relPreviews
            ));
        }

        return new DdlImportDtos.DdlImportPreviewResponse(tablePreviews, globalWarnings);
    }

    @Transactional
    public DdlImportDtos.DdlImportApplyResponse apply(UUID tenantId, DdlImportDtos.DdlImportApplyRequest req) {
        List<ParsedDdlTable> parsed = parseDdl(req.getDdl());
        List<ParsedDdlTable> ordered = topologicalOrder(parsed);
        StorageMode mode = parseStorageMode(req.getStorageMode());
        validateCoreService(mode, req.getCoreBindingService());
        String coreService = mode == StorageMode.CORE_DOMAIN ? req.getCoreBindingService().trim() : null;
        String categoryKey = EntityCategoryKeys.normalizeOrNull(req.getCategoryKey());

        boolean excludeAudit = !Boolean.FALSE.equals(req.getExcludeAuditColumns());
        Set<String> skipSlugs = skipSlugSet(req);

        Map<String, DdlImportDtos.TableApplyOverride> overrideByParsed = new LinkedHashMap<>();
        if (req.getTableOverrides() != null) {
            for (DdlImportDtos.TableApplyOverride o : req.getTableOverrides()) {
                if (o.getParsedEntitySlug() != null && !o.getParsedEntitySlug().isBlank()) {
                    overrideByParsed.put(o.getParsedEntitySlug().trim(), o);
                }
            }
        }

        Map<String, String> finalSlugByParsed = new LinkedHashMap<>();
        for (ParsedDdlTable table : ordered) {
            DdlImportDtos.TableApplyOverride ov = resolveOverride(table, req, ordered.size(), overrideByParsed);
            String finalSlug = resolveFinalEntitySlug(table, ov);
            finalSlugByParsed.put(table.entitySlug(), finalSlug);
        }
        Set<String> distinctFinal = new HashSet<>(finalSlugByParsed.values());
        if (distinctFinal.size() != finalSlugByParsed.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Duplicate entity slugs after overrides");
        }
        for (String fs : distinctFinal) {
            if (entityRepository.existsByTenantIdAndSlug(tenantId, fs)) {
                throw new ApiException(HttpStatus.CONFLICT, "conflict", "Entity slug already exists", Map.of("slug", fs));
            }
        }

        Map<String, UUID> entityIdByParsedSlug = new HashMap<>();
        Set<String> resolvableTargetSlugs = new HashSet<>();
        entityRepository.findAllByTenantIdOrderByNameAsc(tenantId).forEach(e -> resolvableTargetSlugs.add(e.getSlug()));

        List<DdlImportDtos.CreatedEntitySummary> createdEntities = new ArrayList<>();

        for (ParsedDdlTable table : ordered) {
            DdlImportDtos.TableApplyOverride ov = resolveOverride(table, req, ordered.size(), overrideByParsed);
            String name = resolveFinalEntityName(table, ov);
            String slug = resolveFinalEntitySlug(table, ov);

            EntityDefinition entity = schemaService.createEntity(
                    tenantId,
                    name,
                    slug,
                    null,
                    "ACTIVE",
                    categoryKey,
                    false
            );
            entityIdByParsedSlug.put(table.entitySlug(), entity.getId());
            resolvableTargetSlugs.add(slug);
            resolvableTargetSlugs.add(table.entitySlug());

            String defaultDisplay = null;
            List<DdlImportDtos.CreatedFieldSummary> fieldSummaries = new ArrayList<>();
            int sortOrder = 0;

            for (ParsedDdlColumn col : table.columns()) {
                if (skipSlugs.contains(col.columnSlug().toLowerCase(Locale.ROOT))) {
                    continue;
                }
                if (excludeAudit && AUDIT_SLUGS.contains(col.columnSlug().toLowerCase(Locale.ROOT))) {
                    continue;
                }

                ParsedDdlForeignKey fk = col.foreignKey();
                String targetParsed = fk != null ? fkTargetEntitySlug(fk) : null;
                boolean targetResolvable = targetParsed != null
                        && (entityIdByParsedSlug.containsKey(targetParsed) || resolvableTargetSlugs.contains(targetParsed));

                String fieldType = (fk != null && targetResolvable)
                        ? "reference"
                        : mapSqlTypeToFieldType(col.sqlDataType());

                boolean required = col.notNull() && !col.hasDefault();
                String fname = DdlSlugUtil.humanizeSlug(col.columnSlug());
                String config = buildFieldConfig(mode, coreService, col.columnSlug(), targetParsed, fieldType);

                EntityField f = schemaService.createField(
                        tenantId,
                        entity.getId(),
                        fname,
                        col.columnSlug(),
                        fieldType,
                        required,
                        false,
                        config,
                        sortOrder,
                        null,
                        null
                );
                fieldSummaries.add(new DdlImportDtos.CreatedFieldSummary(f.getId(), f.getSlug(), f.getFieldType()));
                sortOrder += 10;

                if (defaultDisplay == null && !col.primaryKeyColumn()
                        && !"id".equalsIgnoreCase(col.columnSlug())
                        && ("string".equals(fieldType) || "text".equals(fieldType))) {
                    defaultDisplay = col.columnSlug();
                }
            }

            if (defaultDisplay == null && !fieldSummaries.isEmpty()) {
                ParsedDdlColumn first = table.columns().stream()
                        .filter(c -> !skipSlugs.contains(c.columnSlug().toLowerCase(Locale.ROOT)))
                        .filter(c -> !(excludeAudit && AUDIT_SLUGS.contains(c.columnSlug().toLowerCase(Locale.ROOT))))
                        .findFirst()
                        .orElse(null);
                if (first != null) {
                    defaultDisplay = first.columnSlug();
                }
            }
            if (defaultDisplay != null) {
                schemaService.updateEntity(
                        tenantId,
                        entity.getId(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        false,
                        Optional.of(defaultDisplay),
                        false,
                        Optional.empty(),
                        Optional.empty()
                );
            }

            createdEntities.add(new DdlImportDtos.CreatedEntitySummary(
                    entity.getId(),
                    entity.getSlug(),
                    entity.getName(),
                    fieldSummaries
            ));
        }

        List<DdlImportDtos.CreatedRelationshipSummary> createdRel = new ArrayList<>();
        if (Boolean.TRUE.equals(req.getCreateRelationshipsFromForeignKeys())) {
            for (ParsedDdlTable table : parsed) {
                UUID childId = entityIdByParsedSlug.get(table.entitySlug());
                if (childId == null) {
                    continue;
                }
                for (ParsedDdlColumn col : table.columns()) {
                    ParsedDdlForeignKey fk = col.foreignKey();
                    if (fk == null) {
                        continue;
                    }
                    String parentParsed = fkTargetEntitySlug(fk);
                    UUID parentId = entityIdByParsedSlug.get(parentParsed);
                    if (parentId == null) {
                        parentId = entityRepository.findByTenantIdAndSlug(tenantId, parentParsed)
                                .map(EntityDefinition::getId)
                                .orElse(null);
                    }
                    if (parentId == null) {
                        continue;
                    }
                    String relSlug = relationshipSlug(table.entitySlug(), col.columnSlug(), parentParsed);
                    String relName = DdlSlugUtil.humanizeSlug(parentParsed) + " → " + DdlSlugUtil.humanizeSlug(table.entitySlug());
                    try {
                        EntityRelationship r = relationshipSchemaService.create(
                                tenantId,
                                relName,
                                relSlug,
                                "one-to-many",
                                parentId,
                                childId,
                                DdlSlugUtil.toSlug(fk.referencedColumnRaw(), 100),
                                col.columnSlug()
                        );
                        createdRel.add(new DdlImportDtos.CreatedRelationshipSummary(r.getId(), r.getSlug()));
                    } catch (ApiException ex) {
                        if (ex.getStatus() == HttpStatus.CONFLICT) {
                            continue;
                        }
                        throw ex;
                    }
                }
            }
        }

        return new DdlImportDtos.DdlImportApplyResponse(createdEntities, createdRel);
    }

    private static DdlImportDtos.TableApplyOverride resolveOverride(
            ParsedDdlTable table,
            DdlImportDtos.DdlImportApplyRequest req,
            int nTables,
            Map<String, DdlImportDtos.TableApplyOverride> overrideByParsed
    ) {
        DdlImportDtos.TableApplyOverride ov = overrideByParsed.get(table.entitySlug());
        if (ov == null && nTables == 1 && req.getTableOverrides() != null) {
            for (DdlImportDtos.TableApplyOverride o : req.getTableOverrides()) {
                if (o.getParsedEntitySlug() == null || o.getParsedEntitySlug().isBlank()) {
                    return o;
                }
            }
        }
        return ov;
    }

    private static String resolveFinalEntityName(ParsedDdlTable table, DdlImportDtos.TableApplyOverride ov) {
        if (ov != null && ov.getEntityName() != null && !ov.getEntityName().isBlank()) {
            return ov.getEntityName().trim();
        }
        return DdlSlugUtil.humanizeSlug(table.entitySlug());
    }

    private static String resolveFinalEntitySlug(ParsedDdlTable table, DdlImportDtos.TableApplyOverride ov) {
        if (ov != null && ov.getEntitySlug() != null && !ov.getEntitySlug().isBlank()) {
            return ov.getEntitySlug().trim();
        }
        return table.entitySlug();
    }

    private static Set<String> skipSlugSet(DdlImportDtos.DdlImportPreviewRequest req) {
        Set<String> skipSlugs = new HashSet<>();
        if (req.getSkipColumnSlugs() != null) {
            for (String s : req.getSkipColumnSlugs()) {
                if (s != null && !s.isBlank()) {
                    skipSlugs.add(s.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        return skipSlugs;
    }

    private static List<ParsedDdlTable> parseDdl(String ddl) {
        try {
            return DdlTableParser.parseCreateTables(ddl);
        } catch (JSQLParserException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "unsupported_ddl", e.getMessage() != null ? e.getMessage() : "DDL parse failed");
        }
    }

    private static void validateCoreService(StorageMode mode, String service) {
        if (mode == StorageMode.CORE_DOMAIN) {
            if (service == null || service.isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "coreBindingService is required when storageMode is CORE_DOMAIN");
            }
        }
    }

    private static StorageMode parseStorageMode(String raw) {
        if (raw == null || raw.isBlank()) {
            return StorageMode.EAV_EXTENSION;
        }
        String u = raw.trim().toUpperCase(Locale.ROOT);
        if ("CORE_DOMAIN".equals(u)) {
            return StorageMode.CORE_DOMAIN;
        }
        if ("EAV_EXTENSION".equals(u) || "EAV".equals(u)) {
            return StorageMode.EAV_EXTENSION;
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "storageMode must be CORE_DOMAIN or EAV_EXTENSION");
    }

    private enum StorageMode {
        CORE_DOMAIN,
        EAV_EXTENSION
    }

    private static String fkTargetEntitySlug(ParsedDdlForeignKey fk) {
        return DdlSlugUtil.toSlug(fk.referencedTableRaw(), 100);
    }

    private static String mapSqlTypeToFieldType(String sqlDataType) {
        if (sqlDataType == null || sqlDataType.isBlank()) {
            return "string";
        }
        String t = sqlDataType.trim().toLowerCase(Locale.ROOT);
        int paren = t.indexOf('(');
        if (paren > 0) {
            t = t.substring(0, paren).trim();
        }
        return switch (t) {
            case "int", "integer", "bigint", "smallint", "tinyint", "serial", "bigserial",
                 "decimal", "numeric", "real", "double", "float", "double precision" -> "number";
            case "boolean", "bool" -> "boolean";
            case "date" -> "date";
            case "timestamp", "timestamptz", "datetime" -> "datetime";
            default -> "string";
        };
    }

    private static String buildFieldConfig(
            StorageMode mode,
            String coreService,
            String columnSlug,
            String targetEntitySlug,
            String fieldType
    ) {
        Map<String, Object> m = new LinkedHashMap<>();
        if ("reference".equals(fieldType) && targetEntitySlug != null) {
            m.put("targetEntitySlug", targetEntitySlug);
        }
        if (mode == StorageMode.CORE_DOMAIN) {
            m.put(FieldStorage.CONFIG_KEY_STORAGE, FieldStorage.STORAGE_CORE_DOMAIN);
            m.put(FieldStorage.CONFIG_KEY_CORE_BINDING, Map.of("service", coreService, "column", columnSlug));
        } else {
            m.put(FieldStorage.CONFIG_KEY_STORAGE, FieldStorage.STORAGE_EAV_EXTENSION);
        }
        return FieldStorage.configJson(m);
    }

    private static String relationshipSlug(String childEntitySlug, String fkColumnSlug, String parentEntitySlug) {
        String raw = childEntitySlug + "_" + fkColumnSlug + "_to_" + parentEntitySlug;
        if (raw.length() <= 100) {
            return raw;
        }
        return raw.substring(0, 100).replaceAll("_+$", "");
    }

    private static List<ParsedDdlTable> topologicalOrder(List<ParsedDdlTable> tables) {
        Map<String, ParsedDdlTable> bySlug = new LinkedHashMap<>();
        for (ParsedDdlTable t : tables) {
            bySlug.put(t.entitySlug(), t);
        }
        Set<String> batch = bySlug.keySet();
        Map<String, Set<String>> childToParents = new HashMap<>();
        Map<String, List<String>> parentToChildren = new HashMap<>();

        for (ParsedDdlTable child : tables) {
            LinkedHashSet<String> parents = new LinkedHashSet<>();
            for (ParsedDdlColumn col : child.columns()) {
                ParsedDdlForeignKey fk = col.foreignKey();
                if (fk == null) {
                    continue;
                }
                String p = fkTargetEntitySlug(fk);
                if (batch.contains(p) && !p.equals(child.entitySlug())) {
                    parents.add(p);
                }
            }
            childToParents.put(child.entitySlug(), parents);
            for (String p : parents) {
                parentToChildren.computeIfAbsent(p, k -> new ArrayList<>()).add(child.entitySlug());
            }
        }

        Map<String, Integer> indegree = new HashMap<>();
        for (ParsedDdlTable t : tables) {
            indegree.put(t.entitySlug(), childToParents.getOrDefault(t.entitySlug(), Set.of()).size());
        }

        Deque<String> queue = new ArrayDeque<>();
        for (Map.Entry<String, Integer> e : indegree.entrySet()) {
            if (e.getValue() == 0) {
                queue.add(e.getKey());
            }
        }

        List<ParsedDdlTable> ordered = new ArrayList<>();
        while (!queue.isEmpty()) {
            String slug = queue.removeFirst();
            ParsedDdlTable tab = bySlug.get(slug);
            if (tab != null) {
                ordered.add(tab);
            }
            for (String c : parentToChildren.getOrDefault(slug, List.of())) {
                int v = indegree.getOrDefault(c, 0) - 1;
                indegree.put(c, v);
                if (v == 0) {
                    queue.add(c);
                }
            }
        }

        if (ordered.size() != tables.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ddl_cycle", "Circular foreign key dependency between tables in DDL");
        }
        return ordered;
    }
}
