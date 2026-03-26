package com.erp.entitybuilder.service;

import com.erp.entitybuilder.domain.EntityField;
import com.erp.entitybuilder.domain.EntityFieldLabel;
import com.erp.entitybuilder.repository.EntityFieldLabelRepository;
import com.erp.entitybuilder.web.ApiException;
import com.erp.entitybuilder.web.RequestLocaleResolver;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class EntityFieldLabelService {

    private final EntityFieldLabelRepository labelRepository;
    private final EntitySchemaService schemaService;

    public EntityFieldLabelService(EntityFieldLabelRepository labelRepository, @Lazy EntitySchemaService schemaService) {
        this.labelRepository = labelRepository;
        this.schemaService = schemaService;
    }

    @Transactional(readOnly = true)
    public Map<UUID, Map<String, String>> labelsByFieldId(Collection<UUID> fieldIds) {
        if (fieldIds == null || fieldIds.isEmpty()) {
            return Map.of();
        }
        List<EntityFieldLabel> rows = labelRepository.findByEntityFieldIdIn(fieldIds);
        Map<UUID, Map<String, String>> out = new HashMap<>();
        for (EntityFieldLabel row : rows) {
            out.computeIfAbsent(row.getEntityFieldId(), k -> new HashMap<>())
                    .put(row.getLocale(), row.getLabel());
        }
        return out;
    }

    /**
     * Picks a display string for the given request language using explicit label rows,
     * then legacy {@link EntityField#getLabelOverride()}, then {@link EntityField#getName()}.
     */
    public static String resolveDisplayLabel(EntityField field, Map<String, String> localeToLabel, String requestLanguage) {
        Map<String, String> m = localeToLabel != null ? localeToLabel : Map.of();
        String lang = RequestLocaleResolver.normalizeLanguage(requestLanguage);
        if (m.containsKey(lang)) {
            return m.get(lang);
        }
        if (m.containsKey("en")) {
            return m.get("en");
        }
        if (field.getLabelOverride() != null && !field.getLabelOverride().isBlank()) {
            return field.getLabelOverride().trim();
        }
        return field.getName();
    }

    @Transactional
    public void upsertLabel(UUID tenantId, UUID entityId, UUID fieldId, String localeTag, String labelText) {
        EntityField field = schemaService.getField(tenantId, entityId, fieldId);
        UUID rowTenant = schemaService.getEntity(tenantId, entityId).getTenantId();
        String loc = RequestLocaleResolver.normalizeLanguage(localeTag);
        if (labelText == null || labelText.isBlank()) {
            labelRepository.deleteByEntityFieldIdAndLocale(fieldId, loc);
            return;
        }
        if (loc.length() > 16) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Locale too long (max 16)");
        }
        if (labelText.length() > 255) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Label too long (max 255)");
        }
        Optional<EntityFieldLabel> existing = labelRepository.findByEntityFieldIdAndLocale(fieldId, loc);
        if (existing.isPresent()) {
            EntityFieldLabel r = existing.get();
            r.setLabel(labelText.trim());
            labelRepository.save(r);
        } else {
            EntityFieldLabel n = new EntityFieldLabel();
            n.setTenantId(rowTenant);
            n.setEntityFieldId(field.getId());
            n.setLocale(loc);
            n.setLabel(labelText.trim());
            labelRepository.save(n);
        }
    }

    /** Keeps the {@code en} row aligned with legacy {@code labelOverride} when the latter is set. */
    @Transactional
    public void syncEnglishRowFromLegacyOverride(UUID tenantId, UUID entityId, EntityField field) {
        String lo = field.getLabelOverride();
        if (lo == null || lo.isBlank()) {
            return;
        }
        upsertLabel(tenantId, entityId, field.getId(), "en", lo.trim());
    }

    /** When legacy {@code labelOverride} is cleared, remove the synced English row so display falls back to {@code name}. */
    @Transactional
    public void deleteSyncedEnglishWhenLegacyCleared(UUID tenantId, UUID entityId, UUID fieldId) {
        schemaService.getField(tenantId, entityId, fieldId);
        labelRepository.deleteByEntityFieldIdAndLocale(fieldId, "en");
    }
}
