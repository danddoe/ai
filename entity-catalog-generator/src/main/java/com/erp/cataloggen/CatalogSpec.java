package com.erp.cataloggen;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CatalogSpec {

    private int version;
    private Defaults defaults;
    private List<EntitySpec> entities = new ArrayList<>();

    public static CatalogSpec read(Path path, ObjectMapper mapper) throws IOException {
        return mapper.readValue(path.toFile(), CatalogSpec.class);
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public Defaults getDefaults() {
        return defaults;
    }

    public void setDefaults(Defaults defaults) {
        this.defaults = defaults;
    }

    public List<EntitySpec> getEntities() {
        return entities;
    }

    public void setEntities(List<EntitySpec> entities) {
        this.entities = entities != null ? entities : new ArrayList<>();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Defaults {
        private String service;
        private String categoryKey;

        public String getService() {
            return service;
        }

        public void setService(String service) {
            this.service = service;
        }

        public String getCategoryKey() {
            return categoryKey;
        }

        public void setCategoryKey(String categoryKey) {
            this.categoryKey = categoryKey;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EntitySpec {
        private String domainClass;
        private String manifestKey;
        private String entitySlug;
        private String entityName;
        private String categoryKey;
        private String description;
        private String defaultDisplayFieldSlug;
        private Boolean includeTenantId;
        private Boolean includeAuditTimestamps;
        private List<String> excludeColumns;
        private String service;
        /** Optional manifest field objects appended after JPA-derived columns (e.g. EAV_EXTENSION). */
        private JsonNode extraFields;

        public String resolvedService(Defaults d) {
            if (service != null && !service.isBlank()) {
                return service;
            }
            return d != null ? d.getService() : null;
        }

        public String resolvedCategoryKey(Defaults d) {
            if (categoryKey != null && !categoryKey.isBlank()) {
                return categoryKey;
            }
            return d != null ? d.getCategoryKey() : null;
        }

        public boolean isIncludeTenantId() {
            return Boolean.TRUE.equals(includeTenantId);
        }

        public boolean isIncludeAuditTimestamps() {
            return Boolean.TRUE.equals(includeAuditTimestamps);
        }

        public String getDomainClass() {
            return domainClass;
        }

        public void setDomainClass(String domainClass) {
            this.domainClass = domainClass;
        }

        public String getManifestKey() {
            return manifestKey;
        }

        public void setManifestKey(String manifestKey) {
            this.manifestKey = manifestKey;
        }

        public String getEntitySlug() {
            return entitySlug;
        }

        public void setEntitySlug(String entitySlug) {
            this.entitySlug = entitySlug;
        }

        public String getEntityName() {
            return entityName;
        }

        public void setEntityName(String entityName) {
            this.entityName = entityName;
        }

        public String getCategoryKey() {
            return categoryKey;
        }

        public void setCategoryKey(String categoryKey) {
            this.categoryKey = categoryKey;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getDefaultDisplayFieldSlug() {
            return defaultDisplayFieldSlug;
        }

        public void setDefaultDisplayFieldSlug(String defaultDisplayFieldSlug) {
            this.defaultDisplayFieldSlug = defaultDisplayFieldSlug;
        }

        public Boolean getIncludeTenantId() {
            return includeTenantId;
        }

        public void setIncludeTenantId(Boolean includeTenantId) {
            this.includeTenantId = includeTenantId;
        }

        public Boolean getIncludeAuditTimestamps() {
            return includeAuditTimestamps;
        }

        public void setIncludeAuditTimestamps(Boolean includeAuditTimestamps) {
            this.includeAuditTimestamps = includeAuditTimestamps;
        }

        public List<String> getExcludeColumns() {
            return excludeColumns != null ? excludeColumns : List.of();
        }

        public void setExcludeColumns(List<String> excludeColumns) {
            this.excludeColumns = excludeColumns;
        }

        public String getService() {
            return service;
        }

        public void setService(String service) {
            this.service = service;
        }

        public JsonNode getExtraFields() {
            return extraFields;
        }

        public void setExtraFields(JsonNode extraFields) {
            this.extraFields = extraFields;
        }
    }
}
