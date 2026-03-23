package com.erp.coreservice.service;

import com.erp.coreservice.audit.CoreDomainAuditService;
import com.erp.coreservice.audit.CoreEntityAuditSnapshots;
import com.erp.coreservice.domain.Property;
import com.erp.coreservice.repository.CompanyRepository;
import com.erp.coreservice.repository.LocationRepository;
import com.erp.coreservice.repository.PropertyRepository;
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
public class PropertyService {

    private final PropertyRepository propertyRepository;
    private final CompanyRepository companyRepository;
    private final LocationRepository locationRepository;
    private final CoreDomainAuditService coreDomainAuditService;

    public PropertyService(
            PropertyRepository propertyRepository,
            CompanyRepository companyRepository,
            LocationRepository locationRepository,
            CoreDomainAuditService coreDomainAuditService
    ) {
        this.propertyRepository = propertyRepository;
        this.companyRepository = companyRepository;
        this.locationRepository = locationRepository;
        this.coreDomainAuditService = coreDomainAuditService;
    }

    @Transactional
    public Property create(UUID tenantId, OrgDtos.CreatePropertyRequest req) {
        companyRepository.findByCompanyIdAndTenantId(req.getCompanyId(), tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Company not found"));
        locationRepository.findByLocationIdAndTenantId(req.getLocationId(), tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Location not found"));
        Property p = new Property();
        p.setTenantId(tenantId);
        p.setCompanyId(req.getCompanyId());
        p.setLocationId(req.getLocationId());
        p.setPropertyName(req.getPropertyName().trim());
        p.setPropertyType(req.getPropertyType().trim());
        Property saved = propertyRepository.save(p);
        coreDomainAuditService.propertyCreated(tenantId, saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public Property get(UUID tenantId, UUID propertyId) {
        return propertyRepository.findByPropertyIdAndTenantId(propertyId, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Property not found"));
    }

    @Transactional(readOnly = true)
    public Page<Property> list(UUID tenantId, int page, int pageSize) {
        int p = Math.max(0, page - 1);
        int size = pageSize <= 0 ? 50 : Math.min(pageSize, 200);
        return propertyRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, PageRequest.of(p, size));
    }

    @Transactional
    public Property patch(UUID tenantId, UUID propertyId, OrgDtos.PatchPropertyRequest req) {
        Property p = get(tenantId, propertyId);
        Map<String, Object> auditBefore = CoreEntityAuditSnapshots.property(p);
        if (req.getPropertyName() != null && !req.getPropertyName().isBlank()) {
            p.setPropertyName(req.getPropertyName().trim());
        }
        if (req.getPropertyType() != null && !req.getPropertyType().isBlank()) {
            p.setPropertyType(req.getPropertyType().trim());
        }
        Property saved = propertyRepository.save(p);
        coreDomainAuditService.propertyUpdated(tenantId, auditBefore, saved);
        return saved;
    }
}
