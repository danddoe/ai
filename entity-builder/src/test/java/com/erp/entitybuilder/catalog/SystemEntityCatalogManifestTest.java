package com.erp.entitybuilder.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validates classpath manifests under {@code system-entity-catalog/} match product conventions:
 * {@code defaultDisplayFieldSlug} must reference a field slug; hybrid entities (any CORE_DOMAIN field)
 * must include at least one non-core (EAV) field for tenant extensions.
 */
class SystemEntityCatalogManifestTest {

    private static final String CATALOG_DIR = "system-entity-catalog/";
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void allIndexedManifestsAreValid() throws Exception {
        JsonNode index = readJson("index.json");
        JsonNode manifests = index.get("manifests");
        assertNotNull(manifests);
        assertTrue(manifests.isArray());
        for (JsonNode keyNode : manifests) {
            String key = keyNode.asText();
            validateManifest(key + ".json");
        }
    }

    private void validateManifest(String fileName) throws Exception {
        JsonNode root = readJson(fileName);
        JsonNode entity = root.get("entity");
        assertNotNull(entity, fileName + ": missing entity");
        String defaultSlug = textOrNull(entity.get("defaultDisplayFieldSlug"));
        assertTrue(defaultSlug != null && !defaultSlug.isBlank(),
                fileName + ": entity.defaultDisplayFieldSlug required");

        JsonNode fieldsNode = root.get("fields");
        assertNotNull(fieldsNode, fileName + ": missing fields");
        assertTrue(fieldsNode.isArray(), fileName + ": fields must be array");

        Set<String> slugs = new HashSet<>();
        boolean anyCore = false;
        boolean anyEav = false;
        for (JsonNode f : fieldsNode) {
            assertTrue(f.hasNonNull("slug"), fileName + ": field missing slug");
            assertTrue(f.hasNonNull("fieldType"), fileName + ": field missing fieldType");
            String slug = f.get("slug").asText();
            assertTrue(slugs.add(slug), fileName + ": duplicate field slug " + slug);
            if (isCoreDomainField(f)) {
                anyCore = true;
            } else {
                anyEav = true;
            }
        }
        assertTrue(slugs.contains(defaultSlug),
                fileName + ": defaultDisplayFieldSlug '" + defaultSlug + "' not found in fields");

        if (anyCore) {
            assertTrue(anyEav, fileName + ": hybrid entity must include at least one EAV_EXTENSION (or non-core) field");
        }
    }

    private static boolean isCoreDomainField(JsonNode f) {
        JsonNode cfg = f.get("config");
        if (cfg == null || cfg.isNull() || !cfg.isObject()) {
            return false;
        }
        JsonNode storage = cfg.get("storage");
        if (storage == null || !storage.isTextual()) {
            return false;
        }
        return "CORE_DOMAIN".equalsIgnoreCase(storage.asText().trim());
    }

    private JsonNode readJson(String relativeName) throws Exception {
        ClassPathResource res = new ClassPathResource(CATALOG_DIR + relativeName);
        assertTrue(res.exists(), "Missing classpath resource: " + CATALOG_DIR + relativeName);
        try (InputStream in = res.getInputStream()) {
            return mapper.readTree(in);
        }
    }

    private static String textOrNull(JsonNode n) {
        if (n == null || n.isNull() || !n.isTextual()) {
            return null;
        }
        return n.asText();
    }
}
