package com.erp.entitybuilder.service;

import com.erp.entitybuilder.domain.EntityDefinition;
import com.erp.entitybuilder.domain.EntityField;
import com.erp.entitybuilder.domain.EntityFieldStatuses;
import com.erp.entitybuilder.repository.EntityDefinitionRepository;
import com.erp.entitybuilder.repository.EntityFieldRepository;
import com.erp.entitybuilder.repository.EntityRecordValueRepository;
import com.erp.entitybuilder.repository.PiiVaultRepository;
import com.erp.entitybuilder.repository.TenantEntityExtensionFieldRepository;
import com.erp.entitybuilder.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class EntityFieldLifecycleService {

    private final EntityFieldRepository fieldRepository;
    private final EntityDefinitionRepository entityRepository;
    private final EntityRecordValueRepository recordValueRepository;
    private final PiiVaultRepository piiVaultRepository;
    private final TenantEntityExtensionFieldRepository tenantEntityExtensionFieldRepository;

    public EntityFieldLifecycleService(
            EntityFieldRepository fieldRepository,
            EntityDefinitionRepository entityRepository,
            EntityRecordValueRepository recordValueRepository,
            PiiVaultRepository piiVaultRepository,
            TenantEntityExtensionFieldRepository tenantEntityExtensionFieldRepository
    ) {
        this.fieldRepository = fieldRepository;
        this.entityRepository = entityRepository;
        this.recordValueRepository = recordValueRepository;
        this.piiVaultRepository = piiVaultRepository;
        this.tenantEntityExtensionFieldRepository = tenantEntityExtensionFieldRepository;
    }

    public enum DeleteOutcome {
        DELETED,
        DEACTIVATED
    }

    public boolean isFieldReferencedByStoredData(UUID fieldId) {
        return recordValueRepository.existsNonNullValueForField(fieldId) || piiVaultRepository.existsByFieldId(fieldId);
    }

    /**
     * Hard-deletes the field when no non-null values (or PII vault rows) exist; otherwise sets {@link EntityFieldStatuses#INACTIVE}
     * and clears {@code required} so records APIs do not treat it as required.
     */
    @Transactional
    public DeleteOutcome deleteOrDeactivateField(EntityDefinition entity, EntityField field) {
        if (!EntityFieldStatuses.isActive(field)) {
            throw new ApiException(HttpStatus.CONFLICT, "conflict", "Field is already inactive", java.util.Map.of("fieldId", field.getId()));
        }
        if (isFieldReferencedByStoredData(field.getId())) {
            field.setStatus(EntityFieldStatuses.INACTIVE);
            field.setRequired(false);
            fieldRepository.save(field);
            UUID extId = entity.getTenantEntityExtensionId();
            if (extId != null) {
                tenantEntityExtensionFieldRepository.findByTenantEntityExtensionIdAndSlug(extId, field.getSlug())
                        .ifPresent(tef -> {
                            tef.setRequired(false);
                            tenantEntityExtensionFieldRepository.save(tef);
                        });
            }
            clearDefaultDisplayIfPointsToField(entity, field.getSlug());
            return DeleteOutcome.DEACTIVATED;
        }
        UUID extId = entity.getTenantEntityExtensionId();
        if (extId != null) {
            tenantEntityExtensionFieldRepository.deleteByTenantEntityExtensionIdAndSlug(extId, field.getSlug());
        }
        fieldRepository.delete(field);
        clearDefaultDisplayIfPointsToField(entity, field.getSlug());
        return DeleteOutcome.DELETED;
    }

    private void clearDefaultDisplayIfPointsToField(EntityDefinition entity, String fieldSlug) {
        String d = entity.getDefaultDisplayFieldSlug();
        if (d != null && d.equals(fieldSlug)) {
            entity.setDefaultDisplayFieldSlug(null);
            entityRepository.save(entity);
        }
    }
}
