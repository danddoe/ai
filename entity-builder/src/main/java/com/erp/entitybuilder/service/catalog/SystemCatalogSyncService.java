package com.erp.entitybuilder.service.catalog;

import com.erp.entitybuilder.domain.EntityDefinition;
import com.erp.entitybuilder.domain.EntityField;
import com.erp.entitybuilder.repository.EntityDefinitionRepository;
import com.erp.entitybuilder.repository.EntityFieldRepository;
import com.erp.entitybuilder.service.EntitySchemaService;
import com.erp.entitybuilder.service.storage.FieldStorage;
import com.erp.entitybuilder.web.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class SystemCatalogSyncService {

    private static final String CATALOG_DIR = "system-entity-catalog/";

    private final ObjectMapper objectMapper;
    private final EntitySchemaService schemaService;
    private final EntityDefinitionRepository entityRepository;
    private final EntityFieldRepository fieldRepository;

    public SystemCatalogSyncService(
            ObjectMapper objectMapper,
            EntitySchemaService schemaService,
            EntityDefinitionRepository entityRepository,
            EntityFieldRepository fieldRepository
    ) {
        this.objectMapper = objectMapper;
        this.schemaService = schemaService;
        this.entityRepository = entityRepository;
        this.fieldRepository = fieldRepository;
    }

    @Transactional
    public CatalogSyncResult sync(UUID tenantId, String manifestKeyOrNull) {
        List<String> keys = readIndexKeys();
        if (manifestKeyOrNull != null && !manifestKeyOrNull.isBlank()) {
            String want = manifestKeyOrNull.trim();
            if (!keys.contains(want)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Unknown manifest key", Map.of("manifestKey", want));
            }
            keys = List.of(want);
        }
        List<String> synced = new ArrayList<>();
        for (String key : keys) {
            syncManifest(tenantId, key);
            synced.add(key);
        }
        return new CatalogSyncResult(synced);
    }

    private void syncManifest(UUID tenantId, String manifestKey) {
        JsonNode root = readManifestJson(manifestKey);
        JsonNode entityNode = root.get("entity");
        if (entityNode == null || !entityNode.hasNonNull("slug")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Manifest missing entity.slug", Map.of("manifestKey", manifestKey));
        }
        String slug = entityNode.get("slug").asText();
        String name = entityNode.hasNonNull("name") ? entityNode.get("name").asText() : slug;
        String description = entityNode.hasNonNull("description") ? entityNode.get("description").asText() : null;
        String categoryKey = entityNode.hasNonNull("categoryKey") ? entityNode.get("categoryKey").asText() : null;
        String defaultDisplay = entityNode.hasNonNull("defaultDisplayFieldSlug")
                ? entityNode.get("defaultDisplayFieldSlug").asText()
                : null;

        EntityDefinition entity = entityRepository.findByTenantIdAndSlug(tenantId, slug).orElse(null);
        if (entity == null) {
            entity = schemaService.createEntity(tenantId, name, slug, description, "ACTIVE", categoryKey);
        } else {
            schemaService.updateEntity(
                    tenantId,
                    entity.getId(),
                    Optional.of(name),
                    Optional.empty(),
                    Optional.ofNullable(description),
                    Optional.empty(),
                    false,
                    Optional.empty(),
                    false,
                    Optional.ofNullable(categoryKey)
            );
        }
        entity = schemaService.getEntity(tenantId, entity.getId());

        JsonNode fieldsNode = root.get("fields");
        if (fieldsNode == null || !fieldsNode.isArray()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Manifest missing fields array", Map.of("manifestKey", manifestKey));
        }
        for (JsonNode f : fieldsNode) {
            upsertField(tenantId, entity.getId(), f);
        }

        if (defaultDisplay != null && !defaultDisplay.isBlank()) {
            schemaService.updateEntity(
                    tenantId,
                    entity.getId(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    false,
                    Optional.of(defaultDisplay.trim()),
                    false,
                    Optional.empty()
            );
        }
    }

    private void upsertField(UUID tenantId, UUID entityId, JsonNode f) {
        String fieldSlug = f.get("slug").asText();
        String fieldName = f.hasNonNull("name") ? f.get("name").asText() : fieldSlug;
        String fieldType = f.get("fieldType").asText();
        boolean required = f.has("required") && f.get("required").asBoolean();
        boolean pii = f.has("pii") && f.get("pii").asBoolean();
        String configJson = null;
        if (f.has("config") && !f.get("config").isNull()) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> cfg = objectMapper.convertValue(f.get("config"), LinkedHashMap.class);
                configJson = FieldStorage.configJson(cfg);
            } catch (Exception e) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Invalid field config in manifest",
                        Map.of("slug", fieldSlug));
            }
        }

        int sortOrderCreate = (f.has("sortOrder") && f.get("sortOrder").isIntegralNumber())
                ? f.get("sortOrder").asInt()
                : 0;
        String labelOverrideCreate = null;
        if (f.has("labelOverride") && !f.get("labelOverride").isNull()) {
            labelOverrideCreate = f.get("labelOverride").asText();
        }

        Optional<Integer> sortOrderUpdate = (f.has("sortOrder") && f.get("sortOrder").isIntegralNumber())
                ? Optional.of(f.get("sortOrder").asInt())
                : Optional.empty();
        Optional<String> labelOverrideUpdate;
        if (!f.has("labelOverride")) {
            labelOverrideUpdate = Optional.empty();
        } else if (f.get("labelOverride").isNull()) {
            labelOverrideUpdate = Optional.of("");
        } else {
            labelOverrideUpdate = Optional.of(f.get("labelOverride").asText());
        }

        String formatStringCreate = null;
        if (f.has("formatString") && !f.get("formatString").isNull()) {
            formatStringCreate = f.get("formatString").asText();
        }

        Optional<String> formatStringUpdate;
        if (!f.has("formatString")) {
            formatStringUpdate = Optional.empty();
        } else if (f.get("formatString").isNull()) {
            formatStringUpdate = Optional.of("");
        } else {
            formatStringUpdate = Optional.of(f.get("formatString").asText());
        }

        Optional<EntityField> existing = fieldRepository.findByEntityIdAndSlug(entityId, fieldSlug);
        if (existing.isEmpty()) {
            schemaService.createField(tenantId, entityId, fieldName, fieldSlug, fieldType, required, pii, configJson, sortOrderCreate, labelOverrideCreate, formatStringCreate);
        } else {
            EntityField ef = existing.get();
            schemaService.updateField(
                    tenantId,
                    entityId,
                    ef.getId(),
                    Optional.of(fieldName),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.of(required),
                    Optional.of(pii),
                    Optional.ofNullable(configJson),
                    sortOrderUpdate,
                    labelOverrideUpdate,
                    formatStringUpdate
            );
        }
    }

    private List<String> readIndexKeys() {
        try (InputStream in = new ClassPathResource(CATALOG_DIR + "index.json").getInputStream()) {
            JsonNode root = objectMapper.readTree(in);
            JsonNode arr = root.get("manifests");
            if (arr == null || !arr.isArray()) {
                return List.of();
            }
            List<String> keys = new ArrayList<>();
            for (JsonNode n : arr) {
                if (n.isTextual()) {
                    keys.add(n.asText());
                }
            }
            return keys;
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read system-entity-catalog/index.json", e);
        }
    }

    private JsonNode readManifestJson(String manifestKey) {
        try (InputStream in = new ClassPathResource(CATALOG_DIR + manifestKey + ".json").getInputStream()) {
            return objectMapper.readTree(in);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.NOT_FOUND, "not_found", "Manifest not found", Map.of("manifestKey", manifestKey));
        }
    }

    public record CatalogSyncResult(List<String> syncedManifestKeys) {}
}
