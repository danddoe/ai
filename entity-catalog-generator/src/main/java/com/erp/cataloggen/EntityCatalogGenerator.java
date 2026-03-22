package com.erp.cataloggen;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class EntityCatalogGenerator {

    private EntityCatalogGenerator() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: EntityCatalogGenerator <catalogSpecPath> <outputDir>");
            System.exit(1);
        }
        Path specPath = Path.of(args[0]);
        Path outputDir = Path.of(args[1]);

        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        CatalogSpec spec = CatalogSpec.read(specPath, mapper);
        CatalogSpec.Defaults defaults = spec.getDefaults();

        // Default overwrite existing manifests; pass -DcatalogGen.overwrite=false or -PcatalogGen.overwrite=false to skip.
        boolean overwrite = !"false".equalsIgnoreCase(System.getProperty("catalogGen.overwrite"));
        boolean updateIndex = "true".equalsIgnoreCase(System.getProperty("catalogGen.updateIndex"));

        List<String> generatedKeys = new ArrayList<>();
        Files.createDirectories(outputDir);

        for (CatalogSpec.EntitySpec entitySpec : spec.getEntities()) {
            if (entitySpec.getDomainClass() == null || entitySpec.getDomainClass().isBlank()) {
                throw new IllegalArgumentException("catalog-spec entity requires domainClass");
            }
            if (entitySpec.getEntityName() == null || entitySpec.getEntityName().isBlank()) {
                throw new IllegalArgumentException("catalog-spec entity requires entityName");
            }
            if (entitySpec.getEntitySlug() == null || entitySpec.getEntitySlug().isBlank()) {
                throw new IllegalArgumentException("catalog-spec entity requires entitySlug");
            }
            String manifestKey = entitySpec.getManifestKey();
            if (manifestKey == null || manifestKey.isBlank()) {
                throw new IllegalArgumentException("catalog-spec entity requires manifestKey");
            }
            Path outFile = outputDir.resolve(manifestKey + ".json");
            if (Files.isRegularFile(outFile) && !overwrite) {
                continue;
            }

            Class<?> entityClass = Class.forName(entitySpec.getDomainClass());
            List<JpaEntityCatalogSource.CatalogField> fields =
                    JpaEntityCatalogSource.extractFields(entityClass, entitySpec);
            if (fields.isEmpty()) {
                throw new IllegalStateException("No @Column fields extracted for " + entitySpec.getDomainClass()
                        + " — adjust excludes or entity mappings.");
            }

            String resolvedService = entitySpec.resolvedService(defaults);
            String resolvedCategory = entitySpec.resolvedCategoryKey(defaults);

            String displaySlug = entitySpec.getDefaultDisplayFieldSlug();
            if (displaySlug == null || displaySlug.isBlank()) {
                displaySlug = fields.stream()
                        .filter(f -> "string".equals(f.fieldType()))
                        .min(Comparator.comparingInt(JpaEntityCatalogSource.CatalogField::sortOrder))
                        .map(JpaEntityCatalogSource.CatalogField::columnName)
                        .orElseGet(() -> fields.isEmpty() ? null : fields.get(0).columnName());
            }

            ManifestDocument doc = new ManifestDocument();
            doc.version = 1;
            doc.manifestKey = manifestKey;
            doc.entity = new EntitySection();
            doc.entity.name = entitySpec.getEntityName();
            doc.entity.slug = entitySpec.getEntitySlug();
            doc.entity.categoryKey = resolvedCategory;
            String desc = entitySpec.getDescription();
            doc.entity.description = (desc != null && !desc.isBlank()) ? desc : null;
            doc.entity.defaultDisplayFieldSlug = displaySlug;

            doc.fields = new ArrayList<>();
            for (JpaEntityCatalogSource.CatalogField cf : fields) {
                FieldSection fs = new FieldSection();
                fs.name = JpaEntityCatalogSource.humanNameForColumn(cf.columnName());
                fs.slug = cf.columnName();
                fs.sortOrder = cf.sortOrder();
                fs.fieldType = cf.fieldType();
                fs.required = cf.required();
                fs.pii = false;
                fs.config = new FieldConfigSection();
                fs.config.storage = "CORE_DOMAIN";
                fs.config.coreBinding = new CoreBindingSection();
                fs.config.coreBinding.service = resolvedService;
                fs.config.coreBinding.column = cf.columnName();
                doc.fields.add(fs);
            }

            appendExtraFieldsFromSpec(mapper, entitySpec, doc);

            mapper.writeValue(outFile.toFile(), doc);
            generatedKeys.add(manifestKey);
        }

        if (updateIndex && !generatedKeys.isEmpty()) {
            Path indexFile = outputDir.resolve("index.json");
            List<String> ordered = new ArrayList<>();
            if (Files.isRegularFile(indexFile)) {
                JsonNode root = mapper.readTree(indexFile.toFile());
                JsonNode manifests = root.get("manifests");
                if (manifests != null && manifests.isArray()) {
                    for (JsonNode n : manifests) {
                        if (n != null && n.isTextual()) {
                            ordered.add(n.asText());
                        }
                    }
                }
            }
            Set<String> seen = new LinkedHashSet<>(ordered);
            for (String k : generatedKeys) {
                if (seen.add(k)) {
                    ordered.add(k);
                }
            }
            ObjectNode out = mapper.createObjectNode();
            ArrayNode arr = mapper.createArrayNode();
            for (String k : ordered) {
                arr.add(k);
            }
            out.set("manifests", arr);
            mapper.writeValue(indexFile.toFile(), out);
        }
    }

    private static void appendExtraFieldsFromSpec(ObjectMapper mapper, CatalogSpec.EntitySpec entitySpec, ManifestDocument doc)
            throws Exception {
        com.fasterxml.jackson.databind.JsonNode extra = entitySpec.getExtraFields();
        if (extra == null || !extra.isArray()) {
            return;
        }
        for (JsonNode n : extra) {
            doc.fields.add(mapper.treeToValue(n, FieldSection.class));
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ManifestDocument {
        public int version = 1;
        public String manifestKey;
        public EntitySection entity;
        public List<FieldSection> fields;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class EntitySection {
        public String name;
        public String slug;
        public String categoryKey;
        public String description;
        public String defaultDisplayFieldSlug;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FieldSection {
        public String name;
        public String slug;
        public int sortOrder;
        public String fieldType;
        public boolean required;
        public boolean pii;
        public FieldConfigSection config;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FieldConfigSection {
        public String storage;
        public CoreBindingSection coreBinding;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CoreBindingSection {
        public String service;
        public String column;
    }
}
