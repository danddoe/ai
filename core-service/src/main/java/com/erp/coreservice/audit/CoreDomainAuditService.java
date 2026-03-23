package com.erp.coreservice.audit;

import com.erp.audit.AuditEvent;
import com.erp.audit.AuditLogWriter;
import com.erp.audit.AuditOperations;
import com.erp.audit.AuditRowDiff;
import com.erp.audit.AuditableResourceSupport;
import com.erp.coreservice.domain.BusinessUnit;
import com.erp.coreservice.domain.Company;
import com.erp.coreservice.domain.Location;
import com.erp.coreservice.domain.PortalHostBinding;
import com.erp.coreservice.domain.Property;
import com.erp.coreservice.domain.PropertyUnit;
import com.erp.coreservice.domain.Region;
import com.erp.coreservice.security.TenantPrincipal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CoreDomainAuditService {

    private static final String SOURCE_SERVICE = "core-service";

    private final AuditLogWriter auditLogWriter;

    public CoreDomainAuditService(@Autowired(required = false) AuditLogWriter auditLogWriter) {
        this.auditLogWriter = auditLogWriter;
    }

    private void attachActorSnapshot(AuditEvent.Builder b) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof TenantPrincipal p)) {
            return;
        }
        String email = p.getEmail();
        if (email == null || email.isBlank()) {
            return;
        }
        Map<String, Object> actor = new LinkedHashMap<>();
        actor.put("email", email.trim());
        b.putPayload("actor", actor);
    }

    private void append(AuditEvent event) {
        if (auditLogWriter == null) {
            return;
        }
        auditLogWriter.append(event);
    }

    private void auditCreate(
            UUID tenantId,
            Class<?> entityClass,
            UUID resourceId,
            Map<String, Object> afterRow
    ) {
        UUID actorId = CoreAuditInvocationContext.actorIdOrNull();
        UUID correlationId = CoreAuditInvocationContext.correlationIdOrNull();
        AuditEvent.Builder b = AuditEvent.builder()
                .tenantId(tenantId)
                .actorId(actorId)
                .sourceService(SOURCE_SERVICE)
                .operation(AuditOperations.CREATE)
                .action(AuditableResourceSupport.action(entityClass, "create"))
                .resourceType(AuditableResourceSupport.resourceType(entityClass))
                .resourceId(resourceId)
                .correlationId(correlationId)
                .putPayload("after", afterRow);
        attachActorSnapshot(b);
        append(b.build());
    }

    private void auditUpdate(
            UUID tenantId,
            Class<?> entityClass,
            UUID resourceId,
            Map<String, Object> beforeRow,
            Map<String, Object> afterRow
    ) {
        List<Map<String, Object>> changes = AuditRowDiff.diffRow(beforeRow, afterRow);
        if (changes.isEmpty()) {
            return;
        }
        UUID actorId = CoreAuditInvocationContext.actorIdOrNull();
        UUID correlationId = CoreAuditInvocationContext.correlationIdOrNull();
        AuditEvent.Builder b = AuditEvent.builder()
                .tenantId(tenantId)
                .actorId(actorId)
                .sourceService(SOURCE_SERVICE)
                .operation(AuditOperations.UPDATE)
                .action(AuditableResourceSupport.action(entityClass, "update"))
                .resourceType(AuditableResourceSupport.resourceType(entityClass))
                .resourceId(resourceId)
                .correlationId(correlationId)
                .putPayload("changes", changes);
        attachActorSnapshot(b);
        append(b.build());
    }

    private void auditDelete(UUID tenantId, Class<?> entityClass, UUID resourceId, Map<String, Object> beforeRow) {
        UUID actorId = CoreAuditInvocationContext.actorIdOrNull();
        UUID correlationId = CoreAuditInvocationContext.correlationIdOrNull();
        AuditEvent.Builder b = AuditEvent.builder()
                .tenantId(tenantId)
                .actorId(actorId)
                .sourceService(SOURCE_SERVICE)
                .operation(AuditOperations.DELETE)
                .action(AuditableResourceSupport.action(entityClass, "delete"))
                .resourceType(AuditableResourceSupport.resourceType(entityClass))
                .resourceId(resourceId)
                .correlationId(correlationId)
                .putPayload("before", beforeRow);
        attachActorSnapshot(b);
        append(b.build());
    }

    public void companyCreated(UUID tenantId, Company saved) {
        auditCreate(tenantId, Company.class, saved.getCompanyId(), CoreEntityAuditSnapshots.company(saved));
    }

    public void companyUpdated(UUID tenantId, Map<String, Object> beforeRow, Company saved) {
        auditUpdate(tenantId, Company.class, saved.getCompanyId(), beforeRow, CoreEntityAuditSnapshots.company(saved));
    }

    public void businessUnitCreated(UUID tenantId, BusinessUnit saved) {
        auditCreate(tenantId, BusinessUnit.class, saved.getBuId(), CoreEntityAuditSnapshots.businessUnit(saved));
    }

    public void businessUnitUpdated(UUID tenantId, Map<String, Object> beforeRow, BusinessUnit saved) {
        auditUpdate(tenantId, BusinessUnit.class, saved.getBuId(), beforeRow, CoreEntityAuditSnapshots.businessUnit(saved));
    }

    public void locationCreated(UUID tenantId, Location saved) {
        auditCreate(tenantId, Location.class, saved.getLocationId(), CoreEntityAuditSnapshots.location(saved));
    }

    public void locationUpdated(UUID tenantId, Map<String, Object> beforeRow, Location saved) {
        auditUpdate(tenantId, Location.class, saved.getLocationId(), beforeRow, CoreEntityAuditSnapshots.location(saved));
    }

    public void regionCreated(UUID tenantId, Region saved) {
        auditCreate(tenantId, Region.class, saved.getRegionId(), CoreEntityAuditSnapshots.region(saved));
    }

    public void regionUpdated(UUID tenantId, Map<String, Object> beforeRow, Region saved) {
        auditUpdate(tenantId, Region.class, saved.getRegionId(), beforeRow, CoreEntityAuditSnapshots.region(saved));
    }

    public void propertyCreated(UUID tenantId, Property saved) {
        auditCreate(tenantId, Property.class, saved.getPropertyId(), CoreEntityAuditSnapshots.property(saved));
    }

    public void propertyUpdated(UUID tenantId, Map<String, Object> beforeRow, Property saved) {
        auditUpdate(tenantId, Property.class, saved.getPropertyId(), beforeRow, CoreEntityAuditSnapshots.property(saved));
    }

    public void propertyUnitCreated(UUID tenantId, PropertyUnit saved) {
        auditCreate(tenantId, PropertyUnit.class, saved.getUnitId(), CoreEntityAuditSnapshots.propertyUnit(saved));
    }

    public void propertyUnitUpdated(UUID tenantId, Map<String, Object> beforeRow, PropertyUnit saved) {
        auditUpdate(tenantId, PropertyUnit.class, saved.getUnitId(), beforeRow, CoreEntityAuditSnapshots.propertyUnit(saved));
    }

    public void portalHostBindingCreated(UUID tenantId, PortalHostBinding saved) {
        auditCreate(tenantId, PortalHostBinding.class, saved.getBindingId(), CoreEntityAuditSnapshots.portalHostBinding(saved));
    }

    public void portalHostBindingUpdated(UUID tenantId, Map<String, Object> beforeRow, PortalHostBinding saved) {
        auditUpdate(
                tenantId,
                PortalHostBinding.class,
                saved.getBindingId(),
                beforeRow,
                CoreEntityAuditSnapshots.portalHostBinding(saved));
    }

    public void portalHostBindingDeleted(UUID tenantId, UUID bindingId, Map<String, Object> beforeRow) {
        auditDelete(tenantId, PortalHostBinding.class, bindingId, beforeRow);
    }
}
