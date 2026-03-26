package com.erp.entitybuilder.service;

import com.erp.entitybuilder.config.PlatformTenantProperties;
import com.erp.entitybuilder.domain.AssignmentScope;
import com.erp.entitybuilder.domain.EntityDefinition;
import com.erp.entitybuilder.domain.EntityStatus;
import com.erp.entitybuilder.domain.EntityStatusAssignment;
import com.erp.entitybuilder.domain.RecordScope;
import com.erp.entitybuilder.repository.EntityStatusAssignmentRepository;
import com.erp.entitybuilder.repository.EntityStatusRepository;
import com.erp.entitybuilder.security.EntityBuilderSecurity;
import com.erp.entitybuilder.web.ApiException;
import com.erp.entitybuilder.web.v1.dto.EntityStatusAssignmentDtos;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class EntityStatusAssignmentService {

    private final EntityStatusAssignmentRepository assignmentRepository;
    private final EntityStatusRepository statusRepository;
    private final EntitySchemaService entitySchemaService;
    private final EntityBuilderSecurity entityBuilderSecurity;
    private final PlatformTenantProperties platformTenantProperties;
    private final EntityStatusLabelService entityStatusLabelService;

    public EntityStatusAssignmentService(
            EntityStatusAssignmentRepository assignmentRepository,
            EntityStatusRepository statusRepository,
            EntitySchemaService entitySchemaService,
            EntityBuilderSecurity entityBuilderSecurity,
            PlatformTenantProperties platformTenantProperties,
            EntityStatusLabelService entityStatusLabelService
    ) {
        this.assignmentRepository = assignmentRepository;
        this.statusRepository = statusRepository;
        this.entitySchemaService = entitySchemaService;
        this.entityBuilderSecurity = entityBuilderSecurity;
        this.platformTenantProperties = platformTenantProperties;
        this.entityStatusLabelService = entityStatusLabelService;
    }

    @Transactional(readOnly = true)
    public List<EntityStatusAssignmentDtos.AssignmentRowDto> listAssignments(UUID tenantId, UUID entityDefinitionId) {
        entitySchemaService.getEntity(tenantId, entityDefinitionId);
        return listByScope(tenantId, AssignmentScope.ENTITY_DEFINITION, entityDefinitionId);
    }

    @Transactional(readOnly = true)
    public List<EntityStatusAssignmentDtos.AssignmentRowDto> listAssignmentsForField(
            UUID tenantId,
            UUID entityId,
            UUID fieldId
    ) {
        entitySchemaService.getField(tenantId, entityId, fieldId);
        return listByScope(tenantId, AssignmentScope.ENTITY_FIELD, fieldId);
    }

    private List<EntityStatusAssignmentDtos.AssignmentRowDto> listByScope(
            UUID tenantId,
            AssignmentScope scope,
            UUID scopeId
    ) {
        List<EntityStatusAssignment> rows = assignmentRepository
                .findByTenantIdAndAssignmentScopeAndScopeIdOrderBySortOrderAsc(tenantId, scope, scopeId);
        List<EntityStatusAssignmentDtos.AssignmentRowDto> out = new ArrayList<>();
        for (EntityStatusAssignment a : rows) {
            EntityStatus st = statusRepository.findById(a.getEntityStatusId())
                    .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error", "Missing entity_status row"));
            entityStatusLabelService.assertCanReadStatus(tenantId, st);
            out.add(new EntityStatusAssignmentDtos.AssignmentRowDto(st.getId(), st.getCode(), st.getLabel(), a.getSortOrder()));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public List<EntityStatusAssignmentDtos.AvailableStatusDto> listAssignableStatuses(UUID tenantId, UUID entityDefinitionId) {
        entitySchemaService.getEntity(tenantId, entityDefinitionId);
        return listAssignableStatusesForTenant(tenantId);
    }

    @Transactional(readOnly = true)
    public List<EntityStatusAssignmentDtos.AvailableStatusDto> listAssignableStatusesForField(
            UUID tenantId,
            UUID entityId,
            UUID fieldId
    ) {
        entitySchemaService.getEntity(tenantId, entityId);
        entitySchemaService.getField(tenantId, entityId, fieldId);
        return listAssignableStatusesForTenant(tenantId);
    }

    @Transactional(readOnly = true)
    public List<EntityStatusAssignmentDtos.AvailableStatusDto> listAssignableStatusesForTenant(UUID tenantId) {
        Map<UUID, EntityStatus> byId = new LinkedHashMap<>();
        if (platformTenantProperties.isConfigured()) {
            UUID pt = platformTenantProperties.getTenantId();
            for (EntityStatus s : statusRepository.findByTenantIdAndRecordScope(pt, RecordScope.STANDARD_RECORD)) {
                byId.putIfAbsent(s.getId(), s);
            }
        }
        for (EntityStatus s : statusRepository.findByTenantIdAndRecordScope(tenantId, RecordScope.TENANT_RECORD)) {
            byId.putIfAbsent(s.getId(), s);
        }
        List<EntityStatus> sorted = new ArrayList<>(byId.values());
        sorted.sort(Comparator.comparing(EntityStatus::getCode, String.CASE_INSENSITIVE_ORDER));
        List<EntityStatusAssignmentDtos.AvailableStatusDto> out = new ArrayList<>();
        for (EntityStatus s : sorted) {
            try {
                entityStatusLabelService.assertCanReadStatus(tenantId, s);
            } catch (ApiException ex) {
                continue;
            }
            out.add(new EntityStatusAssignmentDtos.AvailableStatusDto(s.getId(), s.getCode(), s.getLabel()));
        }
        return out;
    }

    @Transactional
    public List<EntityStatusAssignmentDtos.AssignmentRowDto> replaceAssignments(
            UUID tenantId,
            UUID entityDefinitionId,
            List<UUID> entityStatusIds
    ) {
        EntityDefinition entity = entitySchemaService.getEntity(tenantId, entityDefinitionId);
        if (!entityBuilderSecurity.canMutateEntitySchema(entity)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "forbidden", "Cannot mutate assignments for this entity");
        }
        replaceAssignmentsForScope(tenantId, AssignmentScope.ENTITY_DEFINITION, entityDefinitionId, entityStatusIds);
        return listAssignments(tenantId, entityDefinitionId);
    }

    @Transactional
    public List<EntityStatusAssignmentDtos.AssignmentRowDto> replaceAssignmentsForField(
            UUID tenantId,
            UUID entityId,
            UUID fieldId,
            List<UUID> entityStatusIds
    ) {
        EntityDefinition entity = entitySchemaService.getEntity(tenantId, entityId);
        if (!entityBuilderSecurity.canMutateEntitySchema(entity)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "forbidden", "Cannot mutate assignments for this entity");
        }
        entitySchemaService.getField(tenantId, entityId, fieldId);
        replaceAssignmentsForScope(tenantId, AssignmentScope.ENTITY_FIELD, fieldId, entityStatusIds);
        return listAssignmentsForField(tenantId, entityId, fieldId);
    }

    private void replaceAssignmentsForScope(
            UUID tenantId,
            AssignmentScope scope,
            UUID scopeId,
            List<UUID> entityStatusIds
    ) {
        List<UUID> ordered = entityStatusIds == null ? List.of() : dedupePreserveOrder(entityStatusIds);
        for (UUID sid : ordered) {
            EntityStatus st = statusRepository.findById(sid)
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Unknown entity_status id", Map.of("entityStatusId", sid)));
            entityStatusLabelService.assertCanReadStatus(tenantId, st);
        }
        assignmentRepository.deleteByTenantIdAndAssignmentScopeAndScopeId(tenantId, scope, scopeId);
        assignmentRepository.flush();
        int i = 0;
        for (UUID sid : ordered) {
            EntityStatusAssignment a = new EntityStatusAssignment();
            a.setTenantId(tenantId);
            a.setAssignmentScope(scope);
            a.setScopeId(scopeId);
            a.setEntityStatusId(sid);
            a.setSortOrder(i++);
            assignmentRepository.save(a);
        }
    }

    private static List<UUID> dedupePreserveOrder(List<UUID> in) {
        List<UUID> out = new ArrayList<>();
        var seen = new java.util.HashSet<UUID>();
        for (UUID id : in) {
            if (id != null && seen.add(id)) {
                out.add(id);
            }
        }
        return out;
    }
}
