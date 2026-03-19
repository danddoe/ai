package com.erp.entitybuilder.service;

import com.erp.entitybuilder.domain.EntityDefinition;
import com.erp.entitybuilder.domain.FormLayout;
import com.erp.entitybuilder.repository.EntityDefinitionRepository;
import com.erp.entitybuilder.repository.FormLayoutRepository;
import com.erp.entitybuilder.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class FormLayoutService {

    private final EntityDefinitionRepository entityRepository;
    private final FormLayoutRepository formLayoutRepository;

    public FormLayoutService(EntityDefinitionRepository entityRepository, FormLayoutRepository formLayoutRepository) {
        this.entityRepository = entityRepository;
        this.formLayoutRepository = formLayoutRepository;
    }

    @Transactional(readOnly = true)
    public List<FormLayout> list(UUID tenantId, UUID entityId) {
        // tenant isolation: entity must belong to tenant
        entityRepository.findById(entityId)
                .filter(e -> tenantId.equals(e.getTenantId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Entity not found"));
        return formLayoutRepository.findByTenantIdAndEntityId(tenantId, entityId);
    }

    @Transactional
    public FormLayout create(UUID tenantId, UUID entityId, String name, String layoutJson, boolean isDefault) {
        entityRepository.findById(entityId)
                .filter(e -> tenantId.equals(e.getTenantId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Entity not found"));

        if (formLayoutRepository.findByTenantIdAndEntityIdAndName(tenantId, entityId, name).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "conflict", "Form layout name already exists", Map.of("name", name));
        }

        FormLayout l = new FormLayout();
        l.setTenantId(tenantId);
        l.setEntityId(entityId);
        l.setName(name);
        l.setLayout(layoutJson);
        l.setDefault(isDefault);
        l.setStatus("ACTIVE");

        if (isDefault) {
            // v1: best-effort default enforcement
            List<FormLayout> existing = formLayoutRepository.findByTenantIdAndEntityId(tenantId, entityId);
            for (FormLayout ex : existing) {
                if (ex.isDefault()) {
                    ex.setDefault(false);
                    formLayoutRepository.save(ex);
                }
            }
        }
        return formLayoutRepository.save(l);
    }

    @Transactional(readOnly = true)
    public FormLayout get(UUID tenantId, UUID layoutId) {
        return formLayoutRepository.findById(layoutId)
                .filter(l -> tenantId.equals(l.getTenantId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Form layout not found"));
    }

    @Transactional
    public FormLayout update(UUID tenantId, UUID layoutId, Optional<String> name, Optional<String> layoutJson, Optional<Boolean> isDefault, Optional<String> status) {
        FormLayout l = get(tenantId, layoutId);

        name.filter(v -> v != null && !v.isBlank()).ifPresent(l::setName);
        layoutJson.ifPresent(l::setLayout);
        status.filter(v -> v != null && !v.isBlank()).ifPresent(l::setStatus);

        if (isDefault.isPresent()) {
            boolean newDefault = isDefault.get();
            l.setDefault(newDefault);
            if (newDefault) {
                List<FormLayout> existing = formLayoutRepository.findByTenantIdAndEntityId(tenantId, l.getEntityId());
                for (FormLayout ex : existing) {
                    if (!ex.getId().equals(l.getId()) && ex.isDefault()) {
                        ex.setDefault(false);
                        formLayoutRepository.save(ex);
                    }
                }
            }
        }
        return formLayoutRepository.save(l);
    }

    @Transactional
    public void delete(UUID tenantId, UUID layoutId) {
        FormLayout l = get(tenantId, layoutId);
        formLayoutRepository.delete(l);
    }
}

