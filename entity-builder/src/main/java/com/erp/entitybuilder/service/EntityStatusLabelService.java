package com.erp.entitybuilder.service;

import com.erp.entitybuilder.config.PlatformTenantProperties;
import com.erp.entitybuilder.domain.EntityStatus;
import com.erp.entitybuilder.domain.EntityStatusLabel;
import com.erp.entitybuilder.domain.RecordScope;
import com.erp.entitybuilder.repository.EntityStatusLabelRepository;
import com.erp.entitybuilder.repository.EntityStatusRepository;
import com.erp.entitybuilder.security.EntityBuilderSecurity;
import com.erp.entitybuilder.web.ApiException;
import com.erp.entitybuilder.web.RequestLocaleResolver;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class EntityStatusLabelService {

    private final EntityStatusRepository statusRepository;
    private final EntityStatusLabelRepository labelRepository;
    private final EntityBuilderSecurity entityBuilderSecurity;
    private final PlatformTenantProperties platformTenantProperties;

    public EntityStatusLabelService(
            EntityStatusRepository statusRepository,
            EntityStatusLabelRepository labelRepository,
            EntityBuilderSecurity entityBuilderSecurity,
            PlatformTenantProperties platformTenantProperties
    ) {
        this.statusRepository = statusRepository;
        this.labelRepository = labelRepository;
        this.entityBuilderSecurity = entityBuilderSecurity;
        this.platformTenantProperties = platformTenantProperties;
    }

    @Transactional(readOnly = true)
    public Map<String, String> labelsForStatus(UUID entityStatusId) {
        List<EntityStatusLabel> rows = labelRepository.findByEntityStatusId(entityStatusId);
        Map<String, String> m = new HashMap<>();
        for (EntityStatusLabel row : rows) {
            m.put(row.getLocale(), row.getLabel());
        }
        return m;
    }

    @Transactional(readOnly = true)
    public Map<String, String> listLabelsForRequester(UUID requestTenantId, UUID entityStatusId) {
        EntityStatus st = statusRepository.findById(entityStatusId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Entity status not found"));
        assertCanReadStatus(requestTenantId, st);
        return labelsForStatus(entityStatusId);
    }

    @Transactional(readOnly = true)
    public String resolveDisplayLabel(UUID entityStatusId, String requestLanguage) {
        if (entityStatusId == null) {
            return null;
        }
        EntityStatus st = statusRepository.findById(entityStatusId).orElse(null);
        if (st == null) {
            return null;
        }
        List<EntityStatusLabel> rows = labelRepository.findByEntityStatusId(entityStatusId);
        Map<String, String> m = new HashMap<>();
        for (EntityStatusLabel row : rows) {
            m.put(row.getLocale(), row.getLabel());
        }
        String lang = RequestLocaleResolver.normalizeLanguage(requestLanguage);
        if (m.containsKey(lang)) {
            return m.get(lang);
        }
        if (m.containsKey("en")) {
            return m.get("en");
        }
        return st.getLabel();
    }

    @Transactional
    public void upsertLabel(UUID requestTenantId, UUID entityStatusId, String localeTag, String labelText) {
        EntityStatus st = statusRepository.findById(entityStatusId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Entity status not found"));
        assertCanMutateStatusLabels(requestTenantId, st);

        String loc = RequestLocaleResolver.normalizeLanguage(localeTag);
        if (loc.length() > 16) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Locale too long (max 16)");
        }
        if (labelText == null || labelText.isBlank()) {
            labelRepository.deleteByEntityStatusIdAndLocale(entityStatusId, loc);
            return;
        }
        if (labelText.length() > 255) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Label too long (max 255)");
        }
        Optional<EntityStatusLabel> existing = labelRepository.findByEntityStatusIdAndLocale(entityStatusId, loc);
        if (existing.isPresent()) {
            EntityStatusLabel r = existing.get();
            r.setLabel(labelText.trim());
            labelRepository.save(r);
        } else {
            EntityStatusLabel n = new EntityStatusLabel();
            n.setTenantId(st.getTenantId());
            n.setRecordScope(st.getRecordScope());
            n.setEntityStatusId(entityStatusId);
            n.setLocale(loc);
            n.setLabel(labelText.trim());
            labelRepository.save(n);
        }
    }

    public void assertCanReadStatus(UUID requestTenantId, EntityStatus st) {
        if (requestTenantId.equals(st.getTenantId())) {
            return;
        }
        if (st.getRecordScope() == RecordScope.STANDARD_RECORD
                && platformTenantProperties.isConfigured()
                && platformTenantProperties.getTenantId().equals(st.getTenantId())) {
            return;
        }
        throw new ApiException(HttpStatus.FORBIDDEN, "forbidden", "Cannot access this entity status");
    }

    private void assertCanMutateStatusLabels(UUID requestTenantId, EntityStatus st) {
        if (st.getRecordScope() == RecordScope.STANDARD_RECORD
                && platformTenantProperties.isConfigured()
                && platformTenantProperties.getTenantId().equals(st.getTenantId())) {
            if (!entityBuilderSecurity.canWriteFullSchema()) {
                throw new ApiException(HttpStatus.FORBIDDEN, "forbidden", "Platform status labels require full schema write");
            }
            return;
        }
        if (!requestTenantId.equals(st.getTenantId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "forbidden", "Entity status belongs to another tenant");
        }
        if (!entityBuilderSecurity.canWriteTenantSchema()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "forbidden", "Schema write required");
        }
    }
}
