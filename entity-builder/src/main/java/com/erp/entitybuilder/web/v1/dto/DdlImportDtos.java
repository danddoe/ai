package com.erp.entitybuilder.web.v1.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DdlImportDtos {

    /** {@code CORE_DOMAIN} or {@code EAV_EXTENSION}. */
    public static class DdlImportPreviewRequest {
        @NotBlank
        @Size(max = 200_000)
        private String ddl;

        @Size(max = 32)
        private String storageMode = "EAV_EXTENSION";

        @Size(max = 128)
        private String coreBindingService;

        private Boolean excludeAuditColumns = true;

        private Boolean createRelationshipsFromForeignKeys = false;

        private List<@Size(max = 100) String> skipColumnSlugs = new ArrayList<>();

        @Size(max = 64)
        private String categoryKey;

        public String getDdl() { return ddl; }
        public void setDdl(String ddl) { this.ddl = ddl; }
        public String getStorageMode() { return storageMode; }
        public void setStorageMode(String storageMode) { this.storageMode = storageMode; }
        public String getCoreBindingService() { return coreBindingService; }
        public void setCoreBindingService(String coreBindingService) { this.coreBindingService = coreBindingService; }
        public Boolean getExcludeAuditColumns() { return excludeAuditColumns; }
        public void setExcludeAuditColumns(Boolean excludeAuditColumns) { this.excludeAuditColumns = excludeAuditColumns; }
        public Boolean getCreateRelationshipsFromForeignKeys() { return createRelationshipsFromForeignKeys; }
        public void setCreateRelationshipsFromForeignKeys(Boolean createRelationshipsFromForeignKeys) {
            this.createRelationshipsFromForeignKeys = createRelationshipsFromForeignKeys;
        }
        public List<String> getSkipColumnSlugs() { return skipColumnSlugs; }
        public void setSkipColumnSlugs(List<String> skipColumnSlugs) { this.skipColumnSlugs = skipColumnSlugs != null ? skipColumnSlugs : new ArrayList<>(); }
        public String getCategoryKey() { return categoryKey; }
        public void setCategoryKey(String categoryKey) { this.categoryKey = categoryKey; }
    }

    public static class DdlImportApplyRequest extends DdlImportPreviewRequest {
        private List<TableApplyOverride> tableOverrides = new ArrayList<>();

        public List<TableApplyOverride> getTableOverrides() { return tableOverrides; }
        public void setTableOverrides(List<TableApplyOverride> tableOverrides) {
            this.tableOverrides = tableOverrides != null ? tableOverrides : new ArrayList<>();
        }
    }

    public static class TableApplyOverride {
        /** Parsed entity slug this override applies to; omit or blank to match the only table in single-table DDL. */
        @Size(max = 100)
        private String parsedEntitySlug;

        @Size(max = 255)
        private String entityName;

        @Size(max = 100)
        private String entitySlug;

        public String getParsedEntitySlug() { return parsedEntitySlug; }
        public void setParsedEntitySlug(String parsedEntitySlug) { this.parsedEntitySlug = parsedEntitySlug; }
        public String getEntityName() { return entityName; }
        public void setEntityName(String entityName) { this.entityName = entityName; }
        public String getEntitySlug() { return entitySlug; }
        public void setEntitySlug(String entitySlug) { this.entitySlug = entitySlug; }
    }

    public record FieldPreviewDto(
            String columnName,
            String fieldSlug,
            String sqlDataType,
            String proposedFieldType,
            boolean required,
            boolean primaryKey,
            String fkTargetTable,
            String fkTargetEntitySlug,
            Boolean targetEntityResolvable,
            List<String> warnings
    ) {}

    public record RelationshipPreviewDto(
            String relationshipSlug,
            String name,
            String cardinality,
            String fromEntitySlug,
            String toEntitySlug,
            String fromFieldSlug,
            String toFieldSlug,
            boolean creatableAfterImport,
            String skipReason
    ) {}

    public record TablePreviewDto(
            String rawTableName,
            String proposedEntitySlug,
            String proposedEntityName,
            String defaultDisplayFieldSlug,
            List<FieldPreviewDto> fields,
            List<RelationshipPreviewDto> relationships
    ) {}

    public record DdlImportPreviewResponse(List<TablePreviewDto> tables, List<String> warnings) {}

    public record CreatedFieldSummary(UUID id, String slug, String fieldType) {}

    public record CreatedEntitySummary(UUID id, String slug, String name, List<CreatedFieldSummary> fields) {}

    public record CreatedRelationshipSummary(UUID id, String slug) {}

    public record DdlImportApplyResponse(
            List<CreatedEntitySummary> entities,
            List<CreatedRelationshipSummary> relationships
    ) {}
}
