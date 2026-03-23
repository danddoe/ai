package com.erp.coreservice.service;

import com.erp.coreservice.audit.CoreDomainAuditService;
import com.erp.coreservice.audit.CoreEntityAuditSnapshots;
import com.erp.coreservice.domain.PropertyUnit;
import com.erp.coreservice.repository.PropertyRepository;
import com.erp.coreservice.repository.PropertyUnitRepository;
import com.erp.coreservice.web.ApiException;
import com.erp.coreservice.web.v1.dto.OrgDtos;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class PropertyUnitService {

    private final PropertyUnitRepository propertyUnitRepository;
    private final PropertyRepository propertyRepository;
    private final CoreDomainAuditService coreDomainAuditService;

    public PropertyUnitService(
            PropertyUnitRepository propertyUnitRepository,
            PropertyRepository propertyRepository,
            CoreDomainAuditService coreDomainAuditService
    ) {
        this.propertyUnitRepository = propertyUnitRepository;
        this.propertyRepository = propertyRepository;
        this.coreDomainAuditService = coreDomainAuditService;
    }

    @Transactional
    public PropertyUnit create(UUID tenantId, OrgDtos.CreatePropertyUnitRequest req) {
        propertyRepository.findByPropertyIdAndTenantId(req.getPropertyId(), tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Property not found"));
        PropertyUnit u = new PropertyUnit();
        u.setTenantId(tenantId);
        u.setPropertyId(req.getPropertyId());
        u.setUnitNumber(req.getUnitNumber().trim());
        u.setSquareFootage(req.getSquareFootage());
        u.setStatus(req.getStatus().trim());
        PropertyUnit saved = propertyUnitRepository.save(u);
        coreDomainAuditService.propertyUnitCreated(tenantId, saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public PropertyUnit get(UUID tenantId, UUID unitId) {
        return propertyUnitRepository.findByUnitIdAndTenantId(unitId, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Property unit not found"));
    }

    @Transactional(readOnly = true)
    public Page<PropertyUnit> list(UUID tenantId, UUID propertyId, int page, int pageSize) {
        int p = Math.max(0, page - 1);
        int size = pageSize <= 0 ? 50 : Math.min(pageSize, 200);
        var pr = PageRequest.of(p, size);
        if (propertyId != null) {
            propertyRepository.findByPropertyIdAndTenantId(propertyId, tenantId)
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Property not found"));
            return propertyUnitRepository.findByTenantIdAndPropertyIdOrderByCreatedAtDesc(tenantId, propertyId, pr);
        }
        return propertyUnitRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, pr);
    }

    @Transactional
    public PropertyUnit patch(UUID tenantId, UUID unitId, OrgDtos.PatchPropertyUnitRequest req) {
        PropertyUnit u = get(tenantId, unitId);
        Map<String, Object> auditBefore = CoreEntityAuditSnapshots.propertyUnit(u);
        if (req.getUnitNumber() != null && !req.getUnitNumber().isBlank()) {
            u.setUnitNumber(req.getUnitNumber().trim());
        }
        if (req.isClearSquareFootage()) {
            u.setSquareFootage(null);
        } else if (req.getSquareFootage() != null) {
            u.setSquareFootage(req.getSquareFootage());
        }
        if (req.getStatus() != null && !req.getStatus().isBlank()) {
            u.setStatus(req.getStatus().trim());
        }
        PropertyUnit saved = propertyUnitRepository.save(u);
        coreDomainAuditService.propertyUnitUpdated(tenantId, auditBefore, saved);
        return saved;
    }
}
